package cn.geelato.web.platform.srv.weixin.miniapp.service;

import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppTicketInvalidException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 绑定确认票据（preBindTicket）：把"已验证手机号 → 目标账号"这一事实以 HMAC 签名封存，
 * bind 只认票不认客户端参数，防止"自己手机号预匹配、提交他人账号"的越权绑定。
 * <p>
 * 结构：payload = userId | sha256(phone) 前 16 位（绑定签发时的手机号，防签发后账号手机号变更）
 * | 过期 epoch 秒 | nonce(16 hex)；ticket = base64url(payload) + "." + base64url(HMAC-SHA256(payload, ticketKey))。
 * 一次性：nonce 消费后拉黑至过期（Redis 主用，Redis 异常降级进程内 LocalBoundedCache）。
 */
@Slf4j
@Service
public class MiniAppTicketService {

    private static final String NONCE_REDIS_KEY_PREFIX = "weixin:miniapp:ticket:nonce:";
    /** Redis 不可用时的进程内 nonce 拉黑兜底（TTL 同票据有效期） */
    private final LocalBoundedCache<String, String> localNonceBlacklist =
            new LocalBoundedCache<>("weixin-miniapp-ticket-nonce", 10 * 60 * 1000L, 100_000);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private final WeixinMiniAppConfigurationProperties properties;

    public MiniAppTicketService(WeixinMiniAppConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * 签发绑定确认票据。
     *
     * @param userId 目标账号 ID
     * @param phone  已验证的微信认证手机号（原始值，参与摘要不参与明文）
     */
    public String issue(String userId, String phone) {
        long expireAt = System.currentTimeMillis() / 1000L + properties.getTicketTtlSeconds();
        String payload = userId + "|" + phoneDigest(phone) + "|" + expireAt + "|" + randomNonce();
        return base64Url(payload) + "." + base64Url(hmacSha256(payload));
    }

    /**
     * 校验票据并消费（一次性）。
     *
     * @return 目标账号 userId
     * @throws MiniAppTicketInvalidException 签名不匹配 / 已过期 / nonce 已消费
     */
    public String verifyAndConsume(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            throw new MiniAppTicketInvalidException();
        }
        int dot = ticket.indexOf('.');
        if (dot <= 0 || dot == ticket.length() - 1) {
            throw new MiniAppTicketInvalidException();
        }
        String payload;
        String signature;
        try {
            payload = new String(Base64.getUrlDecoder().decode(ticket.substring(0, dot)), StandardCharsets.UTF_8);
            signature = new String(Base64.getUrlDecoder().decode(ticket.substring(dot + 1)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new MiniAppTicketInvalidException();
        }
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                hmacSha256(payload).getBytes(StandardCharsets.UTF_8))) {
            throw new MiniAppTicketInvalidException();
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 4) {
            throw new MiniAppTicketInvalidException();
        }
        String userId = parts[0];
        long expireAt;
        try {
            expireAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new MiniAppTicketInvalidException();
        }
        long now = System.currentTimeMillis() / 1000L;
        if (now > expireAt) {
            throw new MiniAppTicketInvalidException();
        }
        String nonce = parts[3];
        long remainSeconds = Math.max(1, expireAt - now);
        if (!consumeNonce(nonce, remainSeconds)) {
            throw new MiniAppTicketInvalidException();
        }
        return userId;
    }

    /** 校验票据内手机号摘要与目标账号当前手机号是否一致（防票据签发后账号手机号已变更）。 */
    public boolean phoneMatches(String ticketPhoneDigest, String currentPhone) {
        return ticketPhoneDigest.equals(phoneDigest(currentPhone));
    }

    /** 从已验签 payload 提取手机号摘要（供 bind 一致性校验）。 */
    public String extractPhoneDigest(String payload) {
        String[] parts = payload.split("\\|", -1);
        return parts.length == 4 ? parts[1] : null;
    }

    /** 从已验签 payload 提取 nonce。 */
    public String extractNonce(String payload) {
        String[] parts = payload.split("\\|", -1);
        return parts.length == 4 ? parts[3] : null;
    }

    private boolean consumeNonce(String nonce, long remainSeconds) {
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(NONCE_REDIS_KEY_PREFIX + nonce, "1", remainSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            // 降级路径非原子：多实例部署存在极小重放窗口，仅作单实例兜底
            log.warn("Redis 不可用，preBindTicket nonce 拉黑降级进程内缓存", e);
            if (localNonceBlacklist.exists(nonce)) {
                return false;
            }
            localNonceBlacklist.put(nonce, "1");
            return true;
        }
    }

    private String hmacSha256(String payload) {
        String key = properties.getTicketKey();
        if (key == null || key.isEmpty()) {
            log.error("geelato.wx.miniapp.ticket-key 未配置，无法签发/校验绑定票据");
            throw new MiniAppTicketInvalidException();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sign = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sign);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 初始化失败", e);
        }
    }

    private String phoneDigest(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String randomNonce() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
