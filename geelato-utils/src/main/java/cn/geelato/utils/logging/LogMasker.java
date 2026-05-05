package cn.geelato.utils.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LogMasker {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passWord", "token", "apiKey", "authorization", "secret", "accessToken", "refreshToken"
    );

    private LogMasker() {
    }

    public static String maskToken(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (raw.length() <= 8) {
            return "****";
        }
        return raw.substring(0, 4) + "****" + raw.substring(raw.length() - 4);
    }

    public static Object maskObject(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (isSensitiveKey(key)) {
                    result.put(key, maskToken(String.valueOf(value)));
                } else {
                    result.put(key, maskObject(value));
                }
            }
            return result;
        }
        if (input instanceof List<?> source) {
            List<Object> result = new ArrayList<>(source.size());
            for (Object item : source) {
                result.add(maskObject(item));
            }
            return result;
        }
        return input;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(key) || SENSITIVE_KEYS.contains(normalized);
    }
}
