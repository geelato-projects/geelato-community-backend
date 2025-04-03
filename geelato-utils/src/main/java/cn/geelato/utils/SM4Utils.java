package cn.geelato.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Base64;

public class SM4Utils {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String ALGORITHM_NAME = "SM4";
    private static final String ALGORITHM_MODE = "SM4/ECB/PKCS5Padding";

    public static String encrypt(String data, String keyStr) throws Exception {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] key = keyStr.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, ALGORITHM_NAME);
        Cipher cipher = Cipher.getInstance(ALGORITHM_MODE, "BC");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedBytes = cipher.doFinal(dataBytes);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedData, String keyStr) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] key = keyStr.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, ALGORITHM_NAME);
        Cipher cipher = Cipher.getInstance(ALGORITHM_MODE, "BC");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }


    public static void main(String[] args) {
        try {
            String plainText = "Hello, SM4 Encryption!";
            // 假设这里有一个有效的 16 字节（128 位）密钥字符串
            String keyStr = "1234567890123456";
            String encryptedBase64 = SM4Utils.encrypt(plainText, keyStr);
            System.out.println("Encrypted: " + encryptedBase64);

            String decryptedText = SM4Utils.decrypt(encryptedBase64, keyStr);
            System.out.println("Decrypted: " + decryptedText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
