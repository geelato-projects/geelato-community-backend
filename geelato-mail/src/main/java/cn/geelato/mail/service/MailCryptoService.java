package cn.geelato.mail.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 邮箱凭据加密服务（AES-256-GCM）。
 *
 * <p>KEK 来源：{@code geelato.mail.kek} 配置项（env {@code GEELATO_MAIL_KEK}），
 * 经 SHA-256 派生 256-bit AES 密钥。密文格式：{@code Base64( IV(12B) || ciphertext||GCM-tag )}。
 * 与 AiCryptoService 同模式、独立密钥域（AI 凭据与邮箱凭据不共享 KEK）。
 *
 * <p>KEK 未配置时 {@link #encrypt}/{@link #decrypt} fail-fast 抛
 * {@link IllegalStateException}（诚实暴露，禁止静默降级存明文）。
 */
@Slf4j
@Component
public class MailCryptoService {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final String kek;

    public MailCryptoService(@Value("${geelato.mail.kek:}") String kek) {
        this.kek = kek;
        if (!isAvailable()) {
            log.warn("geelato.mail.kek 未配置（env GEELATO_MAIL_KEK），邮箱账户凭据写入/解密不可用");
        }
    }

    public boolean isAvailable() {
        return kek != null && !kek.isBlank();
    }

    /** 加密明文密码，返回 Base64 密文（含随机 IV） */
    public String encrypt(String plain) {
        ensureKek();
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("邮箱凭据加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密 Base64 密文（GCM tag 校验失败视为密文损坏/KEK 不匹配，fail-fast） */
    public String decrypt(String cipherB64) {
        ensureKek();
        try {
            byte[] all = Base64.getDecoder().decode(cipherB64);
            if (all.length <= IV_LEN) {
                throw new IllegalStateException("密文长度非法");
            }
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("邮箱凭据解密失败（KEK 不匹配或密文损坏）", e);
        }
    }

    private SecretKeySpec keySpec() throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(kek.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private void ensureKek() {
        if (!isAvailable()) {
            throw new IllegalStateException("邮箱凭据主密钥未配置（geelato.mail.kek / env GEELATO_MAIL_KEK）");
        }
    }
}
