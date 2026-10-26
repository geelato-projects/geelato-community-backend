package cn.geelato.script.hostfixture.util;

/** 全部方法签名不兼容 interop，应整类跳过。 */
public final class AllIncompatible {

    private AllIncompatible() {
    }

    public static Class<?> bad() {
        return Object.class;
    }

    public static String pick(String[] candidates) {
        return candidates[0];
    }
}
