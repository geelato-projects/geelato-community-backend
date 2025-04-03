package cn.geelato.core.util;

import cn.geelato.utils.SM4Utils;
import jdk.dynalink.beans.StaticClass;

public class EncryptUtils {
    private final static String key = "0123456789";
    public static String encrypt(String data) {
        String encryptType="SM4";
        return String.format("%s(%s)",encryptType,SM4Utils.encrypt(data,key));
    }

    public static String decrypt(String data) {
        return null;
    }
}
