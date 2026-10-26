package cn.geelato.script.hostfixture.a;

/** 跨库同名类 A（与 hostfixture.b.DupUtil 同简单名，靠库别名隔离）。 */
public final class DupUtil {

    private DupUtil() {
    }

    public static String who() {
        return "a";
    }
}
