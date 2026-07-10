package cn.geelato.it.support.replaydiff;

import java.util.Locale;
import java.util.Set;

public record BodyCaptureConfig(
        boolean enabled,
        int maxBytes,
        Set<String> textContentTypeHints
) {
    public static BodyCaptureConfig defaults() {
        return new BodyCaptureConfig(true, 2 * 1024 * 1024, Set.of(
                "application/json",
                "text/",
                "application/xml",
                "application/xhtml+xml",
                "application/x-www-form-urlencoded"
        ));
    }

    public BodyCaptureConfig {
        if (maxBytes <= 0) {
            maxBytes = 2 * 1024 * 1024;
        }
        if (textContentTypeHints == null || textContentTypeHints.isEmpty()) {
            textContentTypeHints = defaults().textContentTypeHints();
        }
    }

    public boolean shouldTreatAsText(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        for (String hint : textContentTypeHints) {
            if (hint == null || hint.isBlank()) {
                continue;
            }
            String h = hint.toLowerCase(Locale.ROOT);
            if (h.endsWith("/") && ct.startsWith(h)) {
                return true;
            }
            if (!h.endsWith("/") && ct.contains(h)) {
                return true;
            }
        }
        return false;
    }
}

