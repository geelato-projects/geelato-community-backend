package cn.geelato.web.platform.srv.weixin.miniapp.service;

import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.DebugLoginCode;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppErrorCodes;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppRateLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信小程序服务端 API 封装（getuserphonenumber / stable_token）。
 * <p>
 * 注意：平台 {@code HttpUtils} 静态单例无超时设置，本类自建带超时（连接 3s/读 5s）的
 * OkHttp 客户端，避免微信侧抖动拖垮 Tomcat 线程。stable_token 采用 volatile 双检缓存、
 * 提前 600 秒失效（参照 WeixinWorkController.getAccessToken 模式，可按 expires_in 精确过期）。
 * jscode2session 不在本类：经 {@code WeixinCodeResolver} SPI 由
 * {@code DefaultWeixinCodeResolver} 提供（登录 4 接口与 WeixinCode 备用认证器共用，单点实现）。
 */
@Slf4j
@Service
public class WeixinMiniAppApiClient {

    public static final String WX_API_BASE = "https://api.weixin.qq.com";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String TOKEN_CACHE_KEY = "stable_token";
    /** stable_token 提前失效缓冲（秒） */
    private static final long TOKEN_REFRESH_AHEAD_SECONDS = 600L;

    private final WeixinMiniAppConfigurationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    private volatile String cachedAccessToken;
    private volatile long tokenExpireAtMillis;

    public WeixinMiniAppApiClient(WeixinMiniAppConfigurationProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 手机号授权凭证换取微信认证手机号（purePhoneNumber）。
     * access_token 无效（40001）时强制刷新重试 1 次；其余非 0 errcode 抛 20008。
     */
    public String getPhoneNumber(String phoneCode) {
        // ===== [DEBUG-REMOVE-BEGIN] 联调调试（debug-enabled=true）：phoneCode 传 test_{手机号} 直接返回该手机号，不外呼微信。未来整体删除本段 =====
        if (properties.isDebugEnabled()) {
            var debugPhone = DebugLoginCode.parsePhone(phoneCode);
            if (debugPhone.isPresent()) {
                log.warn("[调试] phoneCode 使用模拟手机号 {}（debug-enabled=true，生产严禁开启）", debugPhone.get());
                return debugPhone.get();
            }
        }
        // ===== [DEBUG-REMOVE-END] =====
        JsonNode phoneInfo = postForPhoneInfo(phoneCode, false);
        if (phoneInfo == null) {
            // 40001 access_token 失效：强刷一次再试
            invalidateToken();
            phoneInfo = postForPhoneInfo(phoneCode, true);
        }
        if (phoneInfo == null) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.PHONE_AUTH_FAILED, "手机号授权失败，请重新授权");
        }
        String purePhoneNumber = phoneInfo.path("purePhoneNumber").asText(null);
        if (purePhoneNumber == null || purePhoneNumber.isEmpty()) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.PHONE_AUTH_FAILED, "手机号授权失败，请重新授权");
        }
        return purePhoneNumber;
    }

    /** @return phone_info 节点；errcode=40001 时返回 null 触发上层强刷重试；其余非 0 抛 20008 */
    private JsonNode postForPhoneInfo(String phoneCode, boolean forceRefreshed) {
        try {
            String url = WX_API_BASE + "/wxa/business/getuserphonenumber?access_token=" + getStableToken(forceRefreshed);
            Map<String, Object> body = new HashMap<>();
            body.put("code", phoneCode);
            JsonNode root = postJson(url, body);
            int errcode = root.path("errcode").asInt(0);
            if (errcode == 0) {
                return root.path("phone_info");
            }
            if (errcode == 40001 && !forceRefreshed) {
                log.warn("微信 access_token 失效（40001），强制刷新后重试");
                return null;
            }
            String errmsg = root.path("errmsg").asText();
            log.warn("getuserphonenumber 失败: {} - {}", errcode, errmsg);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.PHONE_AUTH_FAILED, "手机号授权失败，请重新授权");
        } catch (Exception e) {
            log.error("getuserphonenumber 调用异常", e);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.PHONE_AUTH_FAILED, "手机号授权失败，请重新授权");
        }
    }

    /** stable_token（client_credential）：volatile 双检缓存，提前 600 秒失效。 */
    private String getStableToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedAccessToken != null && now < tokenExpireAtMillis) {
            return cachedAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (!forceRefresh && cachedAccessToken != null && now < tokenExpireAtMillis) {
                return cachedAccessToken;
            }
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("grant_type", "client_credential");
                body.put("appid", properties.getAppId());
                body.put("secret", properties.getSecret());
                JsonNode root = postJson(WX_API_BASE + "/cgi-bin/stable_token", body);
                int errcode = root.path("errcode").asInt(0);
                if (errcode != 0) {
                    String errmsg = root.path("errmsg").asText();
                    log.error("获取微信 stable_token 失败: {} - {}（请检查 geelato.wx.miniapp.app-id/secret 配置）", errcode, errmsg);
                    throw new IllegalStateException("获取微信 stable_token 失败: " + errmsg);
                }
                String accessToken = root.path("access_token").asText();
                int expiresIn = root.path("expires_in").asInt(7200);
                cachedAccessToken = accessToken;
                tokenExpireAtMillis = System.currentTimeMillis() + Math.max(60, expiresIn - TOKEN_REFRESH_AHEAD_SECONDS) * 1000L;
                log.info("微信 stable_token 获取成功，有效期 {} 秒", expiresIn);
                return accessToken;
            } catch (IOException e) {
                log.error("获取微信 stable_token 异常", e);
                throw new IllegalStateException("获取微信 stable_token 异常", e);
            }
        }
    }

    private void invalidateToken() {
        tokenExpireAtMillis = 0;
        cachedAccessToken = null;
    }

    private JsonNode postJson(String url, Map<String, Object> body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("微信接口 HTTP " + response.code());
            }
            return objectMapper.readTree(response.body().string());
        }
    }
}
