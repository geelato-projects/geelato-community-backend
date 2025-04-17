package cn.geelato.core.util;

import cn.geelato.utils.SM4Utils;
import jdk.dynalink.beans.StaticClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EncryptUtils {
    private final static String sm4key = "b76278495b7f4df3";
    public static String encrypt(String data) {
        String encryptType="sm4";
        return String.format("%s:%s",encryptType,SM4Utils.encrypt(data,sm4key));
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
                case "sm4" -> SM4Utils.decrypt(encryptData, sm4key);
                default -> data;
            };
        }else {
            decryptData = data;
        }
        return decryptData;
    }
}
