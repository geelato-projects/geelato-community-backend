package cn.geelato.script.hostfixture.b;

/** 跨库同名类 B（与 hostfixture.a.DupUtil 同简单名，靠库别名隔离）。 */
public final class DupUtil {

    private DupUtil() {
    }

    public static String who() {
        return "b";
    }
}
