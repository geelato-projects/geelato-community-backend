package cn.geelato.utils.logging;

import org.slf4j.MDC;

public final class LogContext {
    private LogContext() {
    }

    public static void put(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        MDC.put(key, String.valueOf(value));
    }

    public static String get(String key) {
        return MDC.get(key);
    }

    public static void remove(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        MDC.remove(key);
    }

    public static void clear() {
        MDC.clear();
    }
}
