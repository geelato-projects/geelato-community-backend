package cn.geelato.web.platform.srv.announcement;

import cn.geelato.web.platform.srv.announcement.dto.AnnouncementInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台公告服务：直接读取远程 git 仓库（geelato.announcement.git.repo-uri）内最新一份
 * markdown 公告，结果缓存在内存中，不落盘、无本地目录。
 * <p>公告文件名规范：随机数主键_标题_日期.md（日期 yyyy-MM-dd 或 yyyyMMdd），
 * 仅识别仓库根目录（不递归）。拉取时机：应用启动时一次 + 手动调 refresh 接口；
 * GET /current 只读内存缓存，不出网。</p>
 * <p>不合规文件名记 ERROR 日志后跳过（按文件名去重），不影响平台与其他公告；
 * 启动拉取失败记 ERROR 后继续（无公告），refresh 失败则接口返回失败，不做降级。
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

    private final AnnouncementGitSynchronizer gitSynchronizer;

    /** 已记过 ERROR 的不合规文件名，避免重复拉取时重复刷日志（refresh 时清空重记） */
    private final Set<String> loggedMalformedFiles = ConcurrentHashMap.newKeySet();

    /** 内存缓存的当前最新公告；null 表示无公告（尚未拉取或拉取失败） */
    private volatile AnnouncementInfo current;

    public AnnouncementService(AnnouncementGitSynchronizer gitSynchronizer) {
        this.gitSynchronizer = gitSynchronizer;
    }

    /**
     * 当前最新公告：读内存缓存，不出网、无 IO。
     * 拉取时机为启动与手动 refresh，见 {@link #refresh()}。
     *
     * @return 无公告时为 null
     */
    public AnnouncementInfo getCurrentAnnouncement() {
        return current;
    }

    /**
     * 强制刷新：从 git 仓库拉取（失败向上抛由接口返回失败），解析评选后更新内存缓存并返回。
     * 供运维手动触发（POST /api/announcement/refresh，SystemToken 鉴权）；
     * 清空去重集合使本轮拉入的不合规文件能重新记一次 ERROR。
     */
    public synchronized AnnouncementInfo refresh() {
        this.current = loadLatest(gitSynchronizer.fetchMarkdownFiles());
        AnnouncementInfo info = this.current;
        if (info != null) {
            log.info("[公告] 刷新完成，当前公告：{}（id={}，date={}）",
                    info.getFileName(), info.getId(), info.getDate());
        } else {
            log.info("[公告] 刷新完成，暂无合规公告（仓库内根目录无规范命名的 md 文件）");
        }
        return info;
    }

    /** 应用就绪后拉取一次并记日志（失败不阻断应用，无公告） */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartupAnnouncement() {
        try {
            AnnouncementInfo info = refresh();
            if (info == null) {
                log.info("[公告] 启动完成，暂无公告");
            }
        } catch (Exception e) {
            log.error("[公告] 启动拉取公告仓库失败（不阻断应用）", e);
        }
    }

    /** 从拉取到的文件集合解析文件名规范并评选最新（date 最大，平手按随机数主键字典序） */
    private AnnouncementInfo loadLatest(Map<String, String> files) {
        loggedMalformedFiles.clear();
        return files.entrySet().stream()
                .map(this::parseCandidate)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Candidate::date).thenComparing(Candidate::id))
                .map(c -> new AnnouncementInfo(c.id(), c.title(), c.date().format(ISO_DATE),
                        c.fileName(), files.get(c.fileName())))
                .orElse(null);
    }

    private Candidate parseCandidate(Map.Entry<String, String> file) {
        String fileName = file.getKey();
        String stem = fileName.substring(0, fileName.length() - 3);
        Matcher matcher = FILE_NAME_PATTERN.matcher(stem);
        if (!matcher.matches()) {
            logMalformed(fileName, "不符合 随机数主键_标题_日期 规范");
            return null;
        }
        LocalDate date = parseDate(matcher.group("date"));
        if (date == null) {
            logMalformed(fileName, "日期段无效: " + matcher.group("date"));
            return null;
        }
        return new Candidate(matcher.group("id"), matcher.group("title"), date, fileName);
    }

    /** 日期段解析：yyyy-MM-dd 或 yyyyMMdd；非法日期（如 20261332）返回 null */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, text.length() == 10 ? ISO_DATE : COMPACT_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void logMalformed(String fileName, String reason) {
        if (loggedMalformedFiles.add(fileName)) {
            log.error("[公告] 跳过不合规公告文件（{}）：{}；规范：随机数主键_标题_日期.md（日期 yyyy-MM-dd 或 yyyyMMdd），详见 docs/announcement/README.md",
                    reason, fileName);
        }
    }

    /** 候选公告：文件名解析结果 */
    private record Candidate(String id, String title, LocalDate date, String fileName) {
    }
}
