package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@GraalService(name = "encrypt",built = "true")
public class EncryptService {
    private static final String __AES_KEY__="aes_key";

    public String md5_32bit(String str) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(str.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : messageDigest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String md5_16bit(String str) throws NoSuchAlgorithmException {
        String bit32Str=md5_32bit(str);
        return bit32Str.substring(8,24);
    }

    public String aes(String str) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES");
        byte[] keyBytes = new byte[16];
        System.arraycopy(__AES_KEY__.getBytes(), 0, keyBytes, 0, Math.min(__AES_KEY__.getBytes().length, keyBytes.length));
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedBytes = cipher.doFinal(str.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
