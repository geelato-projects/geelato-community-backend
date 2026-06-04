package cn.geelato.core.util;

import cn.geelato.core.GlobalContext;
import cn.geelato.utils.AesUtils;
import cn.geelato.utils.KeyUtils;
import cn.geelato.utils.RsaUtils;
import cn.geelato.utils.SM2Utils;
import cn.geelato.utils.SM4Utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EncryptUtils {
    public static String encrypt(String data) {
        String encryptType = GlobalContext.getEncryptType();
        String encryptData = switch (encryptType.toLowerCase()) {
            case "aes" -> aesEncrypt(data);
            case "rsa" -> rsaEncrypt(data);
            case "sm2" -> sm2Encrypt(data);
            case "sm4" -> sm4Encrypt(data);
            default -> sm4Encrypt(data);
        };
        return String.format("%s:%s", encryptType, encryptData);
    }

    public static String decrypt(String data) {
        String decryptData;
        String regex = "^([a-zA-Z0-9_]+):(.+)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            String algorithm = matcher.group(1);
            String encryptData = matcher.group(2);
            decryptData = switch (algorithm.toLowerCase()) {
                case "aes" -> aesDecrypt(encryptData);
                case "rsa" -> rsaDecrypt(encryptData);
                case "sm2" -> sm2Decrypt(encryptData);
                case "sm4" -> sm4Decrypt(encryptData);
                default -> data;
            };
        }else {
            decryptData = data;
        }
        return decryptData;
    }

    private static String aesEncrypt(String data) {
        try {
            return AesUtils.encrypt(data, GlobalContext.getAesKey());
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt error.", e);
        }
    }

    private static String aesDecrypt(String data) {
        try {
            return AesUtils.decrypt(data, GlobalContext.getAesKey());
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt error.", e);
        }
    }

    private static String sm4Encrypt(String data) {
        try {
            return SM4Utils.encrypt(data, GlobalContext.getSm4Key());
        } catch (Exception e) {
            throw new IllegalStateException("SM4 encrypt error.", e);
        }
    }

    private static String sm4Decrypt(String data) {
        try {
            return SM4Utils.decrypt(data, GlobalContext.getSm4Key());
        } catch (Exception e) {
            throw new IllegalStateException("SM4 decrypt error.", e);
        }
    }

    private static String sm2Encrypt(String data) {
        try {
            return SM2Utils.encrypt(data, Map.of(
                    KeyUtils.PUBLIC_KEY, requireConfig("SM2 public key", GlobalContext.getSm2PublicKey()),
                    KeyUtils.PRIVATE_KEY, requireConfig("SM2 private key", GlobalContext.getSm2PrivateKey())
            ));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 encrypt error.", e);
        }
    }

    private static String sm2Decrypt(String data) {
        try {
            return SM2Utils.decrypt(data, Map.of(
                    KeyUtils.PUBLIC_KEY, requireConfig("SM2 public key", GlobalContext.getSm2PublicKey()),
                    KeyUtils.PRIVATE_KEY, requireConfig("SM2 private key", GlobalContext.getSm2PrivateKey())
            ));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 decrypt error.", e);
        }
    }

    private static String rsaEncrypt(String data) {
        try {
            return RsaUtils.encrypt(data, requireConfig("RSA public key", GlobalContext.getRsaPublicKey()));
        } catch (Exception e) {
            throw new IllegalStateException("RSA encrypt error.", e);
        }
    }

    private static String rsaDecrypt(String data) {
        try {
            return RsaUtils.decrypt(data, requireConfig("RSA private key", GlobalContext.getRsaPrivateKey()));
        } catch (Exception e) {
            throw new IllegalStateException("RSA decrypt error.", e);
        }
    }

    private static String requireConfig(String configName, String configValue) {
        if (configValue == null || configValue.trim().isEmpty()) {
            throw new IllegalStateException(configName + " is not configured.");
        }
        return configValue.trim();
    }
}
