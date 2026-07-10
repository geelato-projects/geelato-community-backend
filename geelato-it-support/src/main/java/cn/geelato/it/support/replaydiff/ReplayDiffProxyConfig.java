package cn.geelato.it.support.replaydiff;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public record ReplayDiffProxyConfig(
        String listenHost,
        int listenPort,
        URI baselineBaseUri,
        URI candidateBaseUri,
        Path reportFile,
        DiffIgnoreConfig ignoreConfig,
        ProxyResponseMode responseMode,
        int maxJsonDiffEntries,
        BodyCaptureConfig bodyCaptureConfig,
        Set<String> allowedMethods,
        Set<String> baselineHostAllowList
) {
    public ReplayDiffProxyConfig {
        if (listenHost == null || listenHost.isBlank()) {
            listenHost = "0.0.0.0";
        }
        if (listenPort <= 0) {
            throw new IllegalArgumentException("listenPort must be positive");
        }
        if (baselineBaseUri == null) {
            throw new IllegalArgumentException("baselineBaseUri is null");
        }
        if (candidateBaseUri == null) {
            throw new IllegalArgumentException("candidateBaseUri is null");
        }
        if (reportFile == null) {
            throw new IllegalArgumentException("reportFile is null");
        }
        if (ignoreConfig == null) {
            ignoreConfig = DiffIgnoreConfig.empty();
        }
        if (responseMode == null) {
            responseMode = ProxyResponseMode.CANDIDATE;
        }
        if (maxJsonDiffEntries <= 0) {
            maxJsonDiffEntries = 200;
        }
        if (bodyCaptureConfig == null) {
            bodyCaptureConfig = BodyCaptureConfig.defaults();
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = Set.of("GET", "HEAD", "OPTIONS");
        } else {
            allowedMethods = allowedMethods.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (baselineHostAllowList == null) {
            baselineHostAllowList = Set.of();
        } else {
            baselineHostAllowList = baselineHostAllowList.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
