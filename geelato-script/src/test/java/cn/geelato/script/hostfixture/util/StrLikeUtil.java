package cn.geelato.script.hostfixture.util;

import java.util.Map;

/** 模拟第三方静态工具类（私有构造，无注解）。 */
public final class StrLikeUtil {

    private StrLikeUtil() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String join(String a, String b) {
        return a + "-" + b;
    }

    public static String join(String a, String b, String sep) {
        return a + sep + b;
    }

    public static Map<String, Object> info(String key) {
        return Map.of("key", key, "len", key.length());
    }

    public static Class<?> leak(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    public static String varargs(String... parts) {
        return String.join(",", parts);
    }
}
