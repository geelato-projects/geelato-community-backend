package cn.geelato.web.platform.srvlog.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ApiEndpointUsageCollector implements ApplicationListener<ContextClosedEvent> {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ConcurrentMap<Key, Usage> usage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public ApiEndpointUsageCollector(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public void record(String methodKey, String callScope) {
        if (methodKey == null || methodKey.isBlank()) {
            return;
        }
        String scope = normalizeCallScope(callScope);
        Key key = new Key(methodKey, scope);
        Usage u = usage.computeIfAbsent(key, k -> new Usage());
        u.count.increment();
        u.lastSeenEpochMs.set(System.currentTimeMillis());
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        writeSnapshot();
    }

    private void writeSnapshot() {
        if (usage.isEmpty()) {
            return;
        }
        Path dir = resolveDir();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return;
        }
        String ts = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(TS);
        Path file = dir.resolve("api-usage-" + ts + ".json");

        List<Entry> entries = new ArrayList<>(usage.size());
        for (var e : usage.entrySet()) {
            Key k = e.getKey();
            Usage v = e.getValue();
            long lastSeen = v.lastSeenEpochMs.get();
            String lastSeenIso = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastSeen), ZoneId.systemDefault()).toString();
            entries.add(new Entry(k.methodKey, k.callScope, v.count.sum(), lastSeenIso));
        }
        entries.sort(Comparator.comparing(Entry::methodKey).thenComparing(Entry::callScope));
        Snapshot snapshot = new Snapshot(OffsetDateTime.now(ZoneId.systemDefault()).toString(), entries);
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private Path resolveDir() {
        String dir = environment.getProperty("geelato.api-usage.dir");
        if (dir != null && !dir.isBlank()) {
            return Path.of(dir);
        }
        String root = environment.getProperty("geelato.file.root.path");
        if (root == null || root.isBlank()) {
            return null;
        }
        return Path.of(root, "logs", "api-usage");
    }

    private String normalizeCallScope(String callScope) {
        if (callScope == null) {
            return "unknown";
        }
        String v = callScope.trim();
        return v.isEmpty() ? "unknown" : v;
    }

    private record Snapshot(String generatedAt, List<Entry> entries) {
    }

    private record Entry(String methodKey, String callScope, long count, String lastSeen) {
    }

    private record Key(String methodKey, String callScope) {
        private Key {
            Objects.requireNonNull(methodKey, "methodKey");
            Objects.requireNonNull(callScope, "callScope");
        }
    }

    private static final class Usage {
        private final LongAdder count = new LongAdder();
        private final AtomicLong lastSeenEpochMs = new AtomicLong();
    }
}

