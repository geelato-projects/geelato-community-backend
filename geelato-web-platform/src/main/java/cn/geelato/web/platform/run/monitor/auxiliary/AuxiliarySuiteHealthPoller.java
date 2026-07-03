package cn.geelato.web.platform.run.monitor.auxiliary;

import cn.geelato.web.platform.boot.properties.AuxiliarySuiteHealthProperties;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class AuxiliarySuiteHealthPoller {
    private final AuxiliarySuiteHealthProperties properties;
    private final List<AuxiliarySuiteHealthParser> parsers;
    private final AtomicReference<AuxiliarySuiteHealthSummary> latestSummaryRef =
        new AtomicReference<>(new AuxiliarySuiteHealthSummary());
    private final AtomicReference<String> latestErrorRef = new AtomicReference<>(null);
    private ScheduledExecutorService scheduler;

    public AuxiliarySuiteHealthPoller(AuxiliarySuiteHealthProperties properties, List<AuxiliarySuiteHealthParser> parsers) {
        this.properties = properties;
        this.parsers = parsers == null ? Collections.emptyList() : parsers;
    }

    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("auxiliary-suite-health poller disabled.");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("auxiliary-suite-health-poller");
            t.setDaemon(true);
            return t;
        });
        refreshNow();
        long interval = sanitizeInterval(properties.getPollIntervalSeconds());
        scheduler.scheduleWithFixedDelay(this::refreshSafely, interval, interval, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public AuxiliarySuiteHealthSummary getLatestSummary() {
        return fillSummaryMetadata(latestSummaryRef.get());
    }

    public synchronized AuxiliarySuiteHealthSummary refreshNow() {
        AuxiliarySuiteHealthSummary previous = latestSummaryRef.get();
        AuxiliarySuiteHealthSummary current = buildSummary();
        latestSummaryRef.set(current);
        handleAuxiliarySuiteHealthAlertIfNeeded(previous, current);
        return current;
    }

    private void refreshSafely() {
        try {
            refreshNow();
        } catch (Exception e) {
            log.error("auxiliary suite health polling failed", e);
            AuxiliarySuiteHealthSummary summary = latestSummaryRef.get();
            summary = fillSummaryMetadata(summary);
            summary.setLastError(e.getMessage());
            summary.setCheckedAt(System.currentTimeMillis());
            latestSummaryRef.set(summary);
        }
    }

    private AuxiliarySuiteHealthSummary buildSummary() {
        AuxiliarySuiteHealthSummary summary = fillSummaryMetadata(new AuxiliarySuiteHealthSummary());
        summary.setCheckedAt(System.currentTimeMillis());
        latestErrorRef.set(null);

        List<AuxiliarySuiteDefinition> definitions = parseDefinitions();
        if (definitions.isEmpty()) {
            String lastError = latestErrorRef.get();
            summary.setLastError(lastError == null || lastError.trim().isEmpty() ? "未配置辅助套件健康检查" : lastError);
        }
        List<AuxiliarySuiteHealthSnapshot> snapshots = new ArrayList<>();
        for (AuxiliarySuiteDefinition definition : definitions) {
            if (!Boolean.TRUE.equals(definition.getEnabled())) {
                continue;
            }
            snapshots.add(checkSuite(definition));
        }
        summary.setSuites(snapshots);
        summary.setSuiteCount(snapshots.size());
        for (AuxiliarySuiteHealthSnapshot snapshot : snapshots) {
            String status = normalizeCountStatus(snapshot);
            if ("UP".equals(status)) {
                summary.setHealthyCount(summary.getHealthyCount() + 1);
            } else if ("DOWN".equals(status)) {
                summary.setAbnormalCount(summary.getAbnormalCount() + 1);
            } else {
                summary.setUnknownCount(summary.getUnknownCount() + 1);
            }
        }
        summary.setHasFailure(summary.getAbnormalCount() > 0);
        return summary;
    }

    private AuxiliarySuiteHealthSummary fillSummaryMetadata(AuxiliarySuiteHealthSummary summary) {
        AuxiliarySuiteHealthSummary target = summary == null ? new AuxiliarySuiteHealthSummary() : summary;
        Integer interval = properties == null ? null : properties.getPollIntervalSeconds();
        target.setPollIntervalSeconds((int) sanitizeInterval(interval));
        return target;
    }

    private List<AuxiliarySuiteDefinition> parseDefinitions() {
        String suitesJson = properties.getSuitesJson();
        if (suitesJson == null || suitesJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<AuxiliarySuiteDefinition> definitions = JSON.parseArray(suitesJson, AuxiliarySuiteDefinition.class);
            return definitions == null ? Collections.emptyList() : definitions;
        } catch (Exception e) {
            log.error("parse auxiliary suite health definitions failed", e);
            latestErrorRef.set("parse suites-json failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private AuxiliarySuiteHealthSnapshot checkSuite(AuxiliarySuiteDefinition definition) {
        long start = System.currentTimeMillis();
        Integer httpStatus = null;
        String responseBody = null;
        long checkedAt = System.currentTimeMillis();
        try {
            OkHttpClient client = buildClient(definition);
            Request.Builder builder = new Request.Builder().url(definition.getHealthUrl()).get();
            if (definition.getHeaders() != null) {
                definition.getHeaders().forEach((k, v) -> {
                    if (k != null && v != null) {
                        builder.addHeader(k, v);
                    }
                });
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                httpStatus = response.code();
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
            }
            checkedAt = System.currentTimeMillis();
            return parseSnapshot(definition, httpStatus, responseBody, checkedAt, checkedAt - start);
        } catch (Exception e) {
            checkedAt = System.currentTimeMillis();
            AuxiliarySuiteHealthSnapshot snapshot = parseSnapshot(definition, httpStatus, responseBody, checkedAt, checkedAt - start);
            snapshot.setSuccess(false);
            snapshot.setRuntimeStatus("DOWN");
            snapshot.setBusinessStatus("DOWN");
            snapshot.setMessage(e.getMessage());
            return snapshot;
        }
    }

    private AuxiliarySuiteHealthSnapshot parseSnapshot(AuxiliarySuiteDefinition definition, Integer httpStatus, String responseBody, long checkedAt, long durationMs) {
        AuxiliarySuiteHealthParser parser = resolveParser(definition.getParserType());
        AuxiliarySuiteHealthSnapshot snapshot = parser.parse(definition, httpStatus, responseBody, checkedAt, durationMs);
        if (snapshot.getCode() == null) {
            snapshot.setCode(definition.getCode());
        }
        if (snapshot.getName() == null) {
            snapshot.setName(definition.getName());
        }
        if (snapshot.getParserType() == null) {
            snapshot.setParserType(definition.getParserType());
        }
        if (snapshot.getHealthUrl() == null) {
            snapshot.setHealthUrl(definition.getHealthUrl());
        }
        if (snapshot.getCheckedAt() == null) {
            snapshot.setCheckedAt(checkedAt);
        }
        if (snapshot.getDurationMs() == null) {
            snapshot.setDurationMs(durationMs);
        }
        if (snapshot.getEnabled() == null) {
            snapshot.setEnabled(Boolean.TRUE.equals(definition.getEnabled()));
        }
        return snapshot;
    }

    private AuxiliarySuiteHealthParser resolveParser(String parserType) {
        for (AuxiliarySuiteHealthParser parser : parsers) {
            if (parser.supports(parserType)) {
                return parser;
            }
        }
        for (AuxiliarySuiteHealthParser parser : parsers) {
            if (parser.supports("generic")) {
                return parser;
            }
        }
        throw new IllegalStateException("no auxiliary suite health parser found");
    }

    private OkHttpClient buildClient(AuxiliarySuiteDefinition definition) {
        int connectTimeoutSeconds = defaultIfInvalid(definition.getConnectTimeoutSeconds(), properties.getConnectTimeoutSeconds(), 5);
        int readTimeoutSeconds = defaultIfInvalid(definition.getReadTimeoutSeconds(), properties.getReadTimeoutSeconds(), 10);
        return new OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build();
    }

    private int defaultIfInvalid(Integer value, Integer fallback, int defaultValue) {
        if (value != null && value > 0) {
            return value;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        return defaultValue;
    }

    private long sanitizeInterval(Integer value) {
        return value == null || value <= 0 ? 10L : value;
    }

    private String normalizeCountStatus(AuxiliarySuiteHealthSnapshot snapshot) {
        String runtime = snapshot.getRuntimeStatus();
        String business = snapshot.getBusinessStatus();
        if ("DOWN".equalsIgnoreCase(runtime) || "DOWN".equalsIgnoreCase(business)) {
            return "DOWN";
        }
        if ("UP".equalsIgnoreCase(runtime) && ("UP".equalsIgnoreCase(business) || "UNKNOWN".equalsIgnoreCase(business))) {
            return "UP";
        }
        return "UNKNOWN";
    }

    private void handleAuxiliarySuiteHealthAlertIfNeeded(AuxiliarySuiteHealthSummary previous, AuxiliarySuiteHealthSummary current) {
        if (current == null || current.getSuites() == null) {
            return;
        }
        for (AuxiliarySuiteHealthSnapshot currentSnapshot : current.getSuites()) {
            AuxiliarySuiteHealthSnapshot previousSnapshot = findSnapshot(previous, currentSnapshot.getCode());
            boolean currentFailed = Boolean.FALSE.equals(currentSnapshot.getSuccess()) || "DOWN".equalsIgnoreCase(normalizeCountStatus(currentSnapshot));
            boolean previousHealthy = previousSnapshot != null
                && Boolean.TRUE.equals(previousSnapshot.getSuccess())
                && !"DOWN".equalsIgnoreCase(normalizeCountStatus(previousSnapshot));
            if (currentFailed && (previousSnapshot == null || previousHealthy)) {
                log.warn("auxiliary suite health alert reserved for future notifier, code={}, name={}, message={}",
                    currentSnapshot.getCode(), currentSnapshot.getName(), currentSnapshot.getMessage());
            }
        }
    }

    private AuxiliarySuiteHealthSnapshot findSnapshot(AuxiliarySuiteHealthSummary summary, String code) {
        if (summary == null || summary.getSuites() == null || code == null) {
            return null;
        }
        for (AuxiliarySuiteHealthSnapshot snapshot : summary.getSuites()) {
            if (snapshot != null && Objects.equals(code, snapshot.getCode())) {
                return snapshot;
            }
        }
        return null;
    }
}
