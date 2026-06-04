package cn.geelato.utils;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RsaUtils {
    public static String encrypt(String plainText, String publicKeyValue) {
        if (plainText == null) {
            return null;
        }
        if (publicKeyValue == null || publicKeyValue.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA public key is required");
        }
        try {
            PublicKey publicKey = parsePublicKey(publicKeyValue);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipherBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA encrypt failed: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String base64CipherText, String privateKeyValue) {
        if (base64CipherText == null) {
            return null;
        }
        if (privateKeyValue == null || privateKeyValue.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA private key is required");
        }
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(base64CipherText);
            PrivateKey privateKey = parsePrivateKey(privateKeyValue);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA decrypt failed: " + e.getMessage(), e);
        }
    }

    private static PublicKey parsePublicKey(String publicKeyValue) throws Exception {
        String key = publicKeyValue.trim();
        if (key.contains("BEGIN")) {
            key = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
        }
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private static PrivateKey parsePrivateKey(String privateKeyValue) throws Exception {
        String key = privateKeyValue.trim();
        if (key.contains("BEGIN")) {
            key = key
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
        }
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }
}
