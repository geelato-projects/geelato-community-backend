package cn.geelato.web.platform.srv.announcement;

import cn.geelato.web.platform.srv.announcement.dto.AnnouncementInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 平台公告服务：读取配置目录（geelato.announcement.dir）内最新一份 markdown 公告。
 * <p>公告文件名规范：随机数主键_标题_日期.md（日期 yyyy-MM-dd 或 yyyyMMdd），
 * 目录通常由 git 定时拉取维护。每次查询轻量重扫目录（仅 list + 文件名解析，
 * 正文按文件 mtime 缓存），因此 git 拉取到新公告后无需重启即生效。</p>
 * <p>不合规文件名记 ERROR 日志后跳过，不影响平台与其他公告；目录不存在视为无公告。
 * 已读状态由前端 localStorage 维护，服务端不落库。</p>
 *
 * @author geelato
 */
@Slf4j
@Service
public class AnnouncementService {

    /** 文件名（去扩展名）解析：首段随机数主键，中段标题（可含下划线），尾段日期 */
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^(?<id>[^_]+)_(?<title>.+)_(?<date>\\d{4}-\\d{2}-\\d{2}|\\d{8})$");

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${geelato.announcement.dir:announcements}")
    private String announcementDir;

    /** 已记过 ERROR 的不合规文件名，避免每次请求重扫时重复刷日志 */
    private final Set<String> loggedMalformedFiles = ConcurrentHashMap.newKeySet();

    /** "目录不存在"告警只记一次（目录出现后重置） */
    private volatile boolean loggedMissingDir;

    /** 正文缓存：文件路径 + mtime 命中才复用 */
    private volatile ContentCache contentCache;

    /**
     * 获取当前最新公告：目录内日期最大的一份；目录不存在或无合规文件时返回 null。
     *
     * @throws IllegalStateException 目录扫描或正文读取失败（带文件上下文）
     */
    public synchronized AnnouncementInfo getCurrentAnnouncement() {
        Path dir = Paths.get(announcementDir);
        if (!Files.isDirectory(dir)) {
            if (!loggedMissingDir) {
                log.warn("[公告] 目录不存在，暂无公告：{}", dir.toAbsolutePath());
                loggedMissingDir = true;
            }
            contentCache = null;
            return null;
        }
        loggedMissingDir = false;
        List<Candidate> candidates = scanCandidates(dir);
        if (candidates.isEmpty()) {
            return null;
        }
        Candidate latest = candidates.stream()
                .max(Comparator.comparing(Candidate::date)
                        .thenComparing(c -> lastModifiedOf(c.file()))
                        .thenComparing(Candidate::id))
                .orElseThrow();
        return new AnnouncementInfo(latest.id(), latest.title(),
                latest.date().format(ISO_DATE), latest.fileName(), readContent(latest.file()));
    }

    /**
     * 强制刷新：清空正文缓存与日志去重状态后立即重扫，返回最新公告。
     * 供 git 拉取后的运维脚本调用（POST /api/announcement/refresh，SystemToken 鉴权）；
     * 清空去重集合使本轮拉入的不合规文件能重新记一次 ERROR。
     */
    public synchronized AnnouncementInfo refresh() {
        contentCache = null;
        loggedMissingDir = false;
        loggedMalformedFiles.clear();
        AnnouncementInfo info = getCurrentAnnouncement();
        if (info != null) {
            log.info("[公告] 刷新完成，当前公告：{}（id={}，date={}）",
                    info.getFileName(), info.getId(), info.getDate());
        } else {
            log.info("[公告] 刷新完成，暂无合规公告（目录：{}）", Paths.get(announcementDir).toAbsolutePath());
        }
        return info;
    }

    /** 应用就绪后预扫一次并记日志（失败不阻断启动） */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartupAnnouncement() {
        try {
            AnnouncementInfo info = getCurrentAnnouncement();
            if (info != null) {
                log.info("[公告] 启动加载最新公告：{}（id={}，date={}）",
                        info.getFileName(), info.getId(), info.getDate());
            } else {
                log.info("[公告] 暂无公告（目录：{}）", Paths.get(announcementDir).toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("[公告] 启动预扫失败（不阻断应用）", e);
        }
    }

    private List<Candidate> scanCandidates(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .map(this::parseCandidate)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("扫描公告目录失败: " + dir.toAbsolutePath(), e);
        }
    }

    private Candidate parseCandidate(Path file) {
        String fileName = file.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - 3);
        Matcher matcher = FILE_NAME_PATTERN.matcher(stem);
        if (!matcher.matches()) {
            logMalformed(file, "不符合 随机数主键_标题_日期 规范");
            return null;
        }
        LocalDate date = parseDate(matcher.group("date"));
        if (date == null) {
            logMalformed(file, "日期段无效: " + matcher.group("date"));
            return null;
        }
        return new Candidate(matcher.group("id"), matcher.group("title"), date, fileName, file);
    }

    /** 日期段解析：yyyy-MM-dd 或 yyyyMMdd；非法日期（如 20261332）返回 null */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, text.length() == 10 ? ISO_DATE : COMPACT_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 正文读取：命中 (path, mtime) 缓存则复用，否则重读 UTF-8 原文 */
    private String readContent(Path file) {
        long mtime = lastModifiedOf(file);
        ContentCache cached = this.contentCache;
        if (cached != null && cached.file().equals(file) && cached.mtime() == mtime) {
            return cached.content();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            this.contentCache = new ContentCache(file, mtime, content);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("读取公告文件失败: " + file.toAbsolutePath(), e);
        }
    }

    private long lastModifiedOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException("读取公告文件修改时间失败: " + file.toAbsolutePath(), e);
        }
    }

    private void logMalformed(Path file, String reason) {
        if (loggedMalformedFiles.add(file.getFileName().toString())) {
            log.error("[公告] 跳过不合规公告文件（{}）：{}；规范：随机数主键_标题_日期.md（日期 yyyy-MM-dd 或 yyyyMMdd），详见 docs/announcement/README.md",
                    reason, file.toAbsolutePath());
        }
    }

    /** 候选公告：文件名解析结果 */
    private record Candidate(String id, String title, LocalDate date, String fileName, Path file) {
    }

    /** 正文缓存条目 */
    private record ContentCache(Path file, long mtime, String content) {
    }
}
