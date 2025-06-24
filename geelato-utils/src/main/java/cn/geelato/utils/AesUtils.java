package cn.geelato.utils;

import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AesUtils {
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    @SneakyThrows
    public static String encrypt(String content, String key) {
        // 生成密钥
        SecretKeySpec secretKey = generateKey(key);

        // 生成随机IV
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        // 创建AES-GCM加密器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        // 加密
        byte[] encryptedBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        // 拼接IV和密文并转为Base64
        byte[] encryptedWithIv = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, encryptedWithIv, iv.length, encryptedBytes.length);
        return Base64.getEncoder().encodeToString(encryptedWithIv);
    }
    @SneakyThrows
    public static String decrypt(String encryptedContent, String key) {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedContent);

        // 分离IV和密文
        byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, GCM_IV_LENGTH);
        byte[] encryptedBytes = Arrays.copyOfRange(encryptedWithIv, GCM_IV_LENGTH, encryptedWithIv.length);

        // 生成密钥
        SecretKeySpec secretKey = generateKey(key);

        // 创建AES-GCM解密器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        // 解密
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成AES密钥
     * @param key 密钥字符串
     * @return 密钥规范
     * @throws Exception 异常
     */
    private static SecretKeySpec generateKey(String key) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(key.getBytes());
        keyGenerator.init(128, secureRandom);
        SecretKey secretKey = keyGenerator.generateKey();
        return new SecretKeySpec(secretKey.getEncoded(), "AES");
    }

    public static void main(String[] args) {
        String content = "{\n" +
                "\t\"demo_entity\": {\n" +
                "\t\t\"name\": \"svc proxy innvoke\"\n" +
                "\t},\n" +
                "\t\"@biz\": \"0\"\n" +
                "}";
        String key = "b76278495b7f4df3";

        // 加密
        String encrypted = encrypt(content, key);
        System.out.println("加密后: " + encrypted);

        // 解密
        String decrypted = decrypt(encrypted, key);
        System.out.println("解密后: " + decrypted);
    }
}
