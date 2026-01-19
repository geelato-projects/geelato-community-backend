package cn.geelato.web.platform.run;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class PlatformLogSearchService {
    @Autowired(required = false)
    private Environment environment;

    public Optional<LogHit> findFirstByLogTag(String logTag) {
        Path errorDir = resolveErrorLogDir();
        if (errorDir == null) {
            log.warn("未能解析日志目录，无法搜索日志。");
            return Optional.empty();
        }
        List<Path> files = listPlatformExceptionLogFiles(errorDir);
        for (Path file : files) {
            Optional<LogHit> hit = scanFileForTag(file, logTag);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    public List<LogHit> findAllByLogTag(String logTag) {
        Path errorDir = resolveErrorLogDir();
        if (errorDir == null) {
            log.warn("未能解析日志目录，无法搜索日志。");
            return Collections.emptyList();
        }
        List<Path> files = listPlatformExceptionLogFiles(errorDir);
        List<LogHit> results = new ArrayList<>();
        for (Path file : files) {
            scanFileForTag(file, logTag).ifPresent(results::add);
        }
        return results;
    }

    private Optional<LogHit> scanFileForTag(Path file, String logTag) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            List<String> all = lines.toList();
            for (int i = 0; i < all.size(); i++) {
                String line = all.get(i);
                if (line.contains(logTag)) {
                    int j = i + 1;
                    while (j < all.size() && !isRecordStart(all.get(j))) {
                        j++;
                    }
                    int end = Math.min(all.size(), j);
                    List<String> block = all.subList(i, end);
                    return Optional.of(new LogHit(file, i + 1, new ArrayList<>(block)));
                }
            }
        } catch (IOException e) {
            log.warn("读取日志文件失败：{}，原因：{}", file, e.getMessage());
        }
        return Optional.empty();
    }

    private List<Path> listPlatformExceptionLogFiles(Path errorDir) {
        if (!Files.isDirectory(errorDir)) {
            return Collections.emptyList();
        }
        try {
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(errorDir, "platform-runtime-exception.*.log")) {
                for (Path path : stream) {
                    files.add(path);
                }
            }
            files.sort(Comparator.comparingLong(this::safeLastModified).reversed());
            return files;
        } catch (IOException e) {
            log.warn("列举日志目录失败：{}，原因：{}", errorDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    private long safeLastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path resolveErrorLogDir() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (context == null) {
            return null;
        }
        String logDirProp = context.getProperty("LOG_DIR");
        RollingFileAppender<ILoggingEvent> rfa = findPlatformExceptionAppender(context);
        if (rfa == null) {
            return null;
        }
        String file = rfa.getFile();
        if (!isBlank(file)) {
            Path p = Paths.get(file);
            return p.getParent();
        }
        RollingPolicy rp = rfa.getRollingPolicy();
        if (rp instanceof TimeBasedRollingPolicy<?> tbp) {
            String pattern = tbp.getFileNamePattern();
            if (isBlank(pattern)) {
                return null;
            }
            String resolved = pattern;
            if (resolved.contains("${LOG_DIR}") && !isBlank(logDirProp)) {
                resolved = resolved.replace("${LOG_DIR}", logDirProp);
            }
            Path p = Paths.get(resolved);
            return p.getParent();
        }
        return null;
    }

    private RollingFileAppender<ILoggingEvent> findPlatformExceptionAppender(LoggerContext context) {
        Logger logger = context.getLogger("cn.geelato.web.platform.run.PlatformExceptionHandler");
        if (logger != null) {
            for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders(); it.hasNext(); ) {
                Appender<ILoggingEvent> appender = it.next();
                if (appender instanceof RollingFileAppender<?> r
                        && "platformExceptionLogFile".equals(appender.getName())) {
                    @SuppressWarnings("unchecked")
                    RollingFileAppender<ILoggingEvent> casted = (RollingFileAppender<ILoggingEvent>) r;
                    return casted;
                }
            }
        }
        for (Logger l : context.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> it = l.iteratorForAppenders(); it.hasNext(); ) {
                Appender<ILoggingEvent> appender = it.next();
                if (appender instanceof RollingFileAppender<?> r
                        && "platformExceptionLogFile".equals(appender.getName())) {
                    @SuppressWarnings("unchecked")
                    RollingFileAppender<ILoggingEvent> casted = (RollingFileAppender<ILoggingEvent>) r;
                    return casted;
                }
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static final Pattern START = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\s+\\[[^\\]]*\\]\\s+ERROR\\s+.*PlatformExceptionHandler\\s+-\\s+.*");
    private boolean isRecordStart(String s) {
        return s != null && START.matcher(s).matches();
    }

    @Data
    @AllArgsConstructor
    public static class LogHit {
        private Path file;
        private int lineNumber;
        private List<String> lines;
    }
}
