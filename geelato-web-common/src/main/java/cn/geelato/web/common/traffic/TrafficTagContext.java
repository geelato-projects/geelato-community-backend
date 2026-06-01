package cn.geelato.web.common.traffic;

public final class TrafficTagContext {
    private static final ThreadLocal<String> TAG = new ThreadLocal<>();

    private TrafficTagContext() {
    }

    public static void set(String tag) {
        TAG.set(tag);
    }

    public static String get() {
        return TAG.get();
    }

    public static void clear() {
        TAG.remove();
    }
}

