package cn.geelato.web.platform.run;

import org.springframework.stereotype.Service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PlatformLogSearchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static class LogHit {
        private final Path file;
        private final int lineNumber;
        private final List<String> lines;
        public LogHit(Path file, int lineNumber, List<String> lines) {
            this.file = file;
            this.lineNumber = lineNumber;
            this.lines = lines;
        }
        public Path getFile() { return file; }
        public int getLineNumber() { return lineNumber; }
        public List<String> getLines() { return lines; }
    }

    public Optional<LogHit> findFirstByLogTag(String tag) {
        List<Path> files = listLogFiles();
        for (Path file : files) {
            try {
                List<String> all = Files.readAllLines(file);
                for (int i = 0; i < all.size(); i++) {
                    String line = all.get(i);
                    LogMeta meta = parseMeta(line);
                    if (meta != null && tag != null && tag.equals(meta.logTag)) {
                        return Optional.of(new LogHit(file, i + 1, extractFullLog(all, i)));
                    }
                }
            } catch (IOException ignored) { }
        }
        return Optional.empty();
    }

    public List<LogHit> findByUserAndTimeRange(String userId, LocalDateTime from, LocalDateTime to) {
        List<Path> files = listLogFiles();
        List<LogHit> hits = new ArrayList<>();
        for (Path file : files) {
            try {
                List<String> all = Files.readAllLines(file);
                for (int i = 0; i < all.size(); i++) {
                    String line = all.get(i);
                    LogMeta meta = parseMeta(line);
                    if (meta == null) continue;
                    if (meta.occurTime == null) continue;
                    if (meta.occurTime.isBefore(from) || meta.occurTime.isAfter(to)) continue;
                    if (userId != null && !userId.isEmpty() && !userId.equals(meta.userId)) continue;
                    hits.add(new LogHit(file, i + 1, extractFullLog(all, i)));
                }
            } catch (IOException ignored) { }
        }
        hits.sort(Comparator.comparing(LogHit::getFile).thenComparingInt(LogHit::getLineNumber));
        return hits;
    }

    private List<Path> listLogFiles() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("cn.geelato.web.platform.run.PlatformExceptionHandler");
        Appender<ILoggingEvent> appender = logger.getAppender("platformExceptionLogFile");

        if (appender instanceof RollingFileAppender<ILoggingEvent> rfa) {
            if (rfa.getRollingPolicy() instanceof TimeBasedRollingPolicy) {
                TimeBasedRollingPolicy<ILoggingEvent> policy = (TimeBasedRollingPolicy<ILoggingEvent>) rfa.getRollingPolicy();
                String fileNamePattern = policy.getFileNamePattern();
                return findFilesByPattern(fileNamePattern);
            }
        }
        return List.of();
    }

    private List<Path> findFilesByPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) return List.of();

        // Handle path separators
        pattern = pattern.replace('\\', '/');

        // Extract directory and filename prefix
        int lastSlash = pattern.lastIndexOf('/');
        String dirPath = lastSlash != -1 ? pattern.substring(0, lastSlash) : ".";
        String namePattern = lastSlash != -1 ? pattern.substring(lastSlash + 1) : pattern;

        // Simple prefix extraction: everything before the first '%'
        String prefix = namePattern;
        int percentIndex = namePattern.indexOf('%');
        if (percentIndex != -1) {
            prefix = namePattern.substring(0, percentIndex);
        }

        Path logsDir = Paths.get(dirPath);
        if (!Files.exists(logsDir)) return List.of();

        final String filePrefix = prefix;
        try (Stream<Path> s = Files.list(logsDir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(filePrefix))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private LogMeta parseMeta(String line) {
        try {
            if (line == null) return null;
            int logTagIndex = line.indexOf("logTag=");
            int userIdIndex = line.indexOf("|userId=");
            int occurTimeIndex = line.indexOf("|occurTime=");
            if (logTagIndex < 0 || userIdIndex < 0 || occurTimeIndex < 0) return null;
            String logTag = line.substring(logTagIndex + 7, userIdIndex);
            String userId = line.substring(userIdIndex + 8, occurTimeIndex);
            String occurTimeText = line.substring(occurTimeIndex + 11);
            LocalDateTime occurTime = LocalDateTime.parse(occurTimeText, FORMATTER);
            return new LogMeta(logTag, userId, occurTime);
        } catch (Exception e) {
            return null;
        }
    }

    private static class LogMeta {
        private final String logTag;
        private final String userId;
        private final LocalDateTime occurTime;

        private LogMeta(String logTag, String userId, LocalDateTime occurTime) {
            this.logTag = logTag;
            this.userId = userId;
            this.occurTime = occurTime;
        }
    }

    private List<String> extractFullLog(List<String> lines, int index) {
        List<String> result = new ArrayList<>();
        result.add(lines.get(index));
        for (int i = index + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (parseMeta(line) != null) {
                break;
            }
            result.add(line);
        }
        return result;
    }
}
