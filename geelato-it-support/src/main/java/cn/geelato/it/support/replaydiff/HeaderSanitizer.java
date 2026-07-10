package cn.geelato.it.support.replaydiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HeaderSanitizer {
    private HeaderSanitizer() {
    }

    public static Set<String> defaultSensitiveHeaderNames() {
        return Set.of(
                "authorization",
                "cookie",
                "set-cookie",
                "x-api-key",
                "x-auth-token",
                "x-access-token"
        );
    }

    public static Map<String, List<String>> sanitize(Map<String, List<String>> headers) {
        return sanitize(headers, defaultSensitiveHeaderNames(), 2, 2);
    }

    public static Map<String, List<String>> sanitize(
            Map<String, List<String>> headers,
            Set<String> sensitiveHeaderNames,
            int keepPrefix,
            int keepSuffix
    ) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Set<String> sensitive = sensitiveHeaderNames == null ? defaultSensitiveHeaderNames() : sensitiveHeaderNames;
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e == null || e.getKey() == null) {
                continue;
            }
            String name = e.getKey();
            List<String> values = e.getValue();
            if (values == null) {
                continue;
            }
            boolean mask = sensitive.contains(name.toLowerCase(Locale.ROOT));
            List<String> copied = new ArrayList<>(values.size());
            for (String v : values) {
                copied.add(mask ? maskValue(v, keepPrefix, keepSuffix) : v);
            }
            out.put(name, copied);
        }
        return out;
    }

    private static String maskValue(String v, int keepPrefix, int keepSuffix) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.isEmpty()) {
            return s;
        }
        int prefix = Math.max(0, keepPrefix);
        int suffix = Math.max(0, keepSuffix);
        if (s.length() <= prefix + suffix) {
            return "*".repeat(Math.max(1, s.length()));
        }
        String left = s.substring(0, prefix);
        String right = s.substring(s.length() - suffix);
        return left + "*".repeat(Math.max(8, s.length() - prefix - suffix)) + right;
    }
}

