package cn.geelato.web.platform.srvlog.store;

import cn.geelato.web.platform.srvlog.boot.SrvLogProperties;
import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.spi.SrvLogQueryOptions;
import cn.geelato.web.platform.srvlog.spi.SrvLogStore;
import cn.geelato.web.platform.srvlog.spi.SrvSummaryQueryOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class FileSrvLogStore implements SrvLogStore {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SrvLogProperties properties;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    public FileSrvLogStore(SrvLogProperties properties, Environment environment, ObjectMapper objectMapper) {
        this.properties = properties;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(SrvLogRecord record) {
        Path dir = resolveDir();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return;
        }
        Path file = dir.resolve("srv-log-" + LocalDate.now().format(FILE_DATE) + ".jsonl");
        try {
            String line = objectMapper.writeValueAsString(record) + "\n";
            synchronized (lock) {
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public SrvLogPage listExceptions(String methodKey, SrvLogQueryOptions options) {
        List<SrvLogRecord> all = readExceptions(methodKey, options.getStartTime(), options.getEndTime());
        all.sort(Comparator.comparingLong(SrvLogRecord::getTimestamp).reversed());
        int page = Math.max(options.getPage(), 1);
        int size = Math.max(options.getSize(), 1);
        int from = Math.max((page - 1) * size, 0);
        int to = Math.min(from + size, all.size());
        List<SrvLogRecord> slice = from >= to ? Collections.emptyList() : all.subList(from, to);
        SrvLogPage p = new SrvLogPage();
        p.setTotal(all.size());
        p.setPage(page);
        p.setSize(size);
        p.setRecords(new ArrayList<>(slice));
        return p;
    }

    @Override
    public List<SrvExceptionSummary> listRecentExceptionSummary(int days, SrvSummaryQueryOptions options) {
        int topN = options == null ? 200 : Math.max(options.getTopN(), 1);
        long end = System.currentTimeMillis();
        long start = end - Math.max(days, 0L) * 24L * 60L * 60L * 1000L;
        Map<String, SrvExceptionSummary> map = new HashMap<>();
        for (SrvLogRecord r : readRecords(null, start, end)) {
            String key = r.getMethodKey();
            if (key == null) {
                continue;
            }
            SrvExceptionSummary s = map.computeIfAbsent(key, k -> {
                SrvExceptionSummary x = new SrvExceptionSummary();
                x.setMethodKey(k);
                x.setCallCount(0);
                x.setLastCallTime(0L);
                x.setExceptionCount(0);
                x.setLastExceptionTime(0L);
                return x;
            });
            s.setCallCount(s.getCallCount() + 1);
            if (r.getTimestamp() > s.getLastCallTime()) {
                s.setLastCallTime(r.getTimestamp());
            }
            if (!r.isSuccess()) {
                s.setExceptionCount(s.getExceptionCount() + 1);
                if (r.getTimestamp() > s.getLastExceptionTime()) {
                    s.setLastExceptionTime(r.getTimestamp());
                }
            }
        }
        List<SrvExceptionSummary> list = new ArrayList<>(map.values());
        list.removeIf(x -> x.getExceptionCount() <= 0);
        list.sort(Comparator.comparingLong(SrvExceptionSummary::getLastExceptionTime).reversed()
                .thenComparingLong(SrvExceptionSummary::getExceptionCount).reversed());
        if (list.size() > topN) {
            return new ArrayList<>(list.subList(0, topN));
        }
        return list;
    }

    private List<SrvLogRecord> readExceptions(String methodKey, Long startTime, Long endTime) {
        List<SrvLogRecord> list = readRecords(methodKey, startTime, endTime);
        list.removeIf(SrvLogRecord::isSuccess);
        return list;
    }

    private List<SrvLogRecord> readRecords(String methodKey, Long startTime, Long endTime) {
        Path dir = resolveDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        long start = startTime == null ? 0L : startTime;
        long end = endTime == null ? Long.MAX_VALUE : endTime;
        LocalDate startDate = start == 0L ? LocalDate.now() : Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end == Long.MAX_VALUE ? LocalDate.now() : Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate();

        List<SrvLogRecord> list = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Path file = dir.resolve("srv-log-" + d.format(FILE_DATE) + ".jsonl");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    SrvLogRecord r;
                    try {
                        r = objectMapper.readValue(line, SrvLogRecord.class);
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (methodKey != null && !methodKey.isBlank() && !methodKey.equals(r.getMethodKey())) {
                        continue;
                    }
                    long ts = r.getTimestamp();
                    if (ts < start || ts > end) {
                        continue;
                    }
                    list.add(r);
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private Path resolveDir() {
        String dir = properties.getFileDir();
        if (dir != null && !dir.isBlank()) {
            return Path.of(dir);
        }
        String root = environment.getProperty("geelato.file.root.path");
        if (root == null || root.isBlank()) {
            return null;
        }
        return Path.of(root, "logs", "srv-log");
    }
}

