package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailLabel;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.util.MailSessionCtx;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件消息服务：本地读模型（mail_message）的查询/写入/批量操作/同步落库。
 *
 * <p>读路径全部走本地表（IMAP 同步落库 + SMTP 发送副本），不穿透协议层。
 *
 * <p>文件夹语义：
 * <ul>
 *   <li>物理文件夹（落库 folder 列）：inbox/sent/draft/trash/spam/archive/custom_{folderId}</li>
 *   <li>虚拟视图（不落库，查询时映射为 flags 条件）：starred/todo/important</li>
 * </ul>
 *
 * <p>已知的 Fluent DSL 限制：FilterAdapter 以首个 Filter 的 logic 决定整组 AND/OR（扁平组，
 * 不支持括号嵌套），故 keyword 裸词搜索 P0 仅匹配 subject；多字段 OR 搜索列入 P1。
 *
 * <p>关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailMessageService {

    /** 物理文件夹（可落库 folder 列的合法值，custom_* 单独匹配） */
    private static final Set<String> PHYSICAL_FOLDERS = Set.of(
            "inbox", "sent", "draft", "trash", "spam", "archive");

    /** 草稿扩展字段白名单（draft_ext_json 允许存取/回显的 key；encryptPassword 等敏感字段禁入） */
    static final Set<String> DRAFT_EXT_KEYS = Set.of(
            "inReplyTo", "forwardFrom", "forwardAsAttachment", "signatureId",
            "isSeparateSend", "scheduleSendAt", "isEncrypted", "requestReadReceipt");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailLabelService labelService;

    // ==================== 查询 ====================

    /**
     * 分页查询邮件列表。
     *
     * @param params 查询参数（folder 支持物理文件夹与 starred/todo 虚拟视图）
     * @return 前端 MailListPage 契约：{list,total,page,pageSize,hasMore}
     */
    public Map<String, Object> list(MailListParams params) {
        if ("conversation".equals(params.viewMode)) {
            return listConversations(params);
        }
        String userId = MailSessionCtx.getCurrentUserId();
        int page = Math.max(1, params.page);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (params.accountId != null && !params.accountId.isBlank()) {
            filters.add(Filter.eq("accountId", params.accountId));
        }
        applyFolderFilter(filters, params.folder);
        applySearchFilters(filters, params);

        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(filters.toArray(new Filter[0]))
                .order(resolveOrder(params.sortField, params.sortOrder));
        PageResult<Map<String, Object>> pageResult = query.page(page, pageSize).page();

        List<MailMessage> entities = pageResult.getRecords().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        // 批量解析本页邮件的标签（一次查询，避免逐封 N+1）
        Set<String> allLabelIds = new HashSet<>();
        for (MailMessage msg : entities) {
            allLabelIds.addAll(parseIdArray(msg.getLabelIds()));
        }
        Map<String, MailLabel> labelMap = labelService.mapByIds(allLabelIds);
        List<Map<String, Object>> items = entities.stream()
                .map(msg -> toResponse(msg, labelMap))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /** 查询单封邮件详情（校验归属当前用户；不存在/越权返回 null） */
    public MailMessage getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("id", id),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    // ==================== 会话视图（P1-F4，V82） ====================

    /** 主题回复/转发前缀（循环剥离，支持 "Re: Fwd: 回复: x" 链式前缀；中英文冒号兼容） */
    private static final java.util.regex.Pattern SUBJECT_REPLY_PREFIX = java.util.regex.Pattern
            .compile("^\\s*(?i:(re|fw|fwd|回复|答复|转发|自动回复))[:：]\\s*");

    /**
     * 会话视图列表（P1-F4）：按 in_reply_to → message_id 链（union-find）归组，
     * 无引用关系的孤立邮件回退规范化主题归组；线程按最新邮件时间倒序分页。
     *
     * <p>归组规则（对齐 Gmail/QQ 邮箱口径）：
     * 1. in_reply_to 解析到本集合内邮件 → 同一引用链线程（threadId=ref:链根 messageId）
     * 2. in_reply_to 悬空（父邮件不在本集合，如对方邮件未同步/在其他文件夹）→
     *    共享同一悬空锚点的回复归组（threadId=ref:悬空 messageId）
     * 3. 无引用关系 → 规范化主题归组（剥离 Re:/Fwd:/回复: 等前缀，大小写不敏感）；
     *    规范化主题为空时不合并（避免 "无主题"/空白主题互相误并），各自成线程
     *
     * @return 前端 MailConversationPage 契约：{conversations,total,page,pageSize,hasMore}
     */
    public Map<String, Object> listConversations(MailListParams params) {
        String userId = MailSessionCtx.getCurrentUserId();
        int page = Math.max(1, params.page);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (params.accountId != null && !params.accountId.isBlank()) {
            filters.add(Filter.eq("accountId", params.accountId));
        }
        applyFolderFilter(filters, params.folder);
        applySearchFilters(filters, params);

        // 会话归组需要全量匹配行（线程可跨页），按发件时间倒序一次取回
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("sendDate"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        List<MailMessage> all = rows.stream().map(this::toEntity).collect(Collectors.toList());
        List<ConversationAggregate> aggregates = groupThreads(all);
        // 5) 线程级分页
        int total = aggregates.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<ConversationAggregate> pageAggregates = aggregates.subList(from, to);
        // 6) 本页线程的标签批量解析（一次查询，避免逐封 N+1）
        Set<String> allLabelIds = new HashSet<>();
        for (ConversationAggregate agg : pageAggregates) {
            for (MailMessage m : agg.mails()) {
                allLabelIds.addAll(parseIdArray(m.getLabelIds()));
            }
        }
        Map<String, MailLabel> labelMap = labelService.mapByIds(allLabelIds);
        // 7) 响应装配
        List<Map<String, Object>> conversations = new ArrayList<>();
        for (ConversationAggregate agg : pageAggregates) {
            MailMessage latest = agg.latest();
            int unread = 0;
            boolean hasAttachment = false;
            boolean starred = false;
            for (MailMessage m : agg.mails()) {
                if ("unread".equals(m.getReadStatus())) {
                    unread++;
                }
                if (m.getHasAttachment() == 1) {
                    hasAttachment = true;
                }
                if (parseFlags(m.getFlagsJson()).contains("starred")) {
                    starred = true;
                }
            }
            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("threadId", agg.threadId());
            conv.put("subject", displaySubject(latest));
            conv.put("count", agg.mails().size());
            conv.put("unreadCount", unread);
            conv.put("latestDate", latest.getSendDate() == null ? "" : latest.getSendDate().toInstant().toString());
            conv.put("hasAttachment", hasAttachment);
            conv.put("starred", starred);
            List<Map<String, Object>> items = agg.mails().stream()
                    .map(m -> toResponse(m, labelMap))
                    .collect(Collectors.toList());
            conv.put("mails", items);
            conversations.add(conv);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversations", conversations);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < total);
        return result;
    }

    /**
     * 会话归组纯函数（包级 seam，单测直接覆盖）：
     * 输入匹配邮件全集（顺序不限），输出按最新邮件时间倒序的线程聚合列表。
     */
    static List<ConversationAggregate> groupThreads(List<MailMessage> all) {
        // 1) message_id 索引（引用链反查）
        Map<String, MailMessage> byMessageId = new HashMap<>();
        for (MailMessage m : all) {
            if (m.getMessageId() != null && !m.getMessageId().isBlank()) {
                byMessageId.putIfAbsent(m.getMessageId(), m);
            }
        }
        // 2) union-find：in_reply_to 可解析到本集合内邮件时合并线程
        Map<String, String> parent = new HashMap<>();
        for (MailMessage m : all) {
            parent.put(m.getId(), m.getId());
        }
        for (MailMessage m : all) {
            String ref = m.getInReplyTo();
            if (ref == null || ref.isBlank()) {
                continue;
            }
            MailMessage target = byMessageId.get(ref);
            if (target != null && !target.getId().equals(m.getId())) {
                union(parent, m.getId(), target.getId());
            }
        }
        // 3) 分组：union 组 >1 → 引用链线程；孤立邮件 → 悬空锚点或规范化主题归组
        Map<String, List<MailMessage>> byRoot = new LinkedHashMap<>();
        for (MailMessage m : all) {
            byRoot.computeIfAbsent(find(parent, m.getId()), k -> new ArrayList<>()).add(m);
        }
        Map<String, List<MailMessage>> threads = new LinkedHashMap<>();
        for (List<MailMessage> group : byRoot.values()) {
            if (group.size() > 1) {
                MailMessage anchor = resolveThreadAnchor(group);
                String anchorId = anchor.getMessageId() != null && !anchor.getMessageId().isBlank()
                        ? anchor.getMessageId() : anchor.getId();
                threads.put("ref:" + anchorId, group);
            } else {
                MailMessage m = group.get(0);
                if (m.getInReplyTo() != null && !m.getInReplyTo().isBlank()) {
                    threads.computeIfAbsent("ref:" + m.getInReplyTo(), k -> new ArrayList<>()).add(m);
                } else {
                    String normalized = normalizeSubject(m.getSubject());
                    if (normalized.isBlank()) {
                        // 空白/空前缀主题不合并，避免 "无主题" 邮件互相误并
                        threads.put("solo:" + m.getId(), new ArrayList<>(List.of(m)));
                    } else {
                        threads.computeIfAbsent("subj:" + normalized.toLowerCase(java.util.Locale.ROOT),
                                k -> new ArrayList<>()).add(m);
                    }
                }
            }
        }
        // 4) 线程聚合：组内按发件时间倒序，线程间按最新邮件时间倒序
        List<ConversationAggregate> aggregates = new ArrayList<>();
        for (Map.Entry<String, List<MailMessage>> e : threads.entrySet()) {
            List<MailMessage> mails = e.getValue();
            mails.sort(MailMessageService::compareBySendDateDesc);
            aggregates.add(new ConversationAggregate(e.getKey(), mails));
        }
        aggregates.sort((a, b) -> compareBySendDateDesc(a.latest(), b.latest()));
        return aggregates;
    }

    private static int compareBySendDateDesc(MailMessage a, MailMessage b) {
        Date da = a.getSendDate();
        Date db = b.getSendDate();
        if (da == null && db == null) {
            return 0;
        }
        if (da == null) {
            return 1;
        }
        if (db == null) {
            return -1;
        }
        return db.compareTo(da);
    }

    /** 线程锚点（链根）：in_reply_to 为空或指向组外的成员；多候选/环回时取最早发件时间 */
    private static MailMessage resolveThreadAnchor(List<MailMessage> group) {
        Set<String> memberMsgIds = group.stream()
                .map(MailMessage::getMessageId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        MailMessage anchor = null;
        for (MailMessage m : group) {
            String ref = m.getInReplyTo();
            boolean isRoot = ref == null || ref.isBlank() || !memberMsgIds.contains(ref);
            if (isRoot && (anchor == null || earlier(m, anchor))) {
                anchor = m;
            }
        }
        if (anchor == null) {
            for (MailMessage m : group) {
                if (anchor == null || earlier(m, anchor)) {
                    anchor = m;
                }
            }
        }
        return anchor;
    }

    private static boolean earlier(MailMessage a, MailMessage b) {
        Date da = a.getSendDate();
        Date db = b.getSendDate();
        if (da == null) {
            return false;
        }
        if (db == null) {
            return true;
        }
        return da.before(db);
    }

    /** 会话展示主题：剥离回复/转发前缀；剥离后为空则回退原始主题 */
    private String displaySubject(MailMessage msg) {
        String raw = msg.getSubject() == null ? "" : msg.getSubject();
        String stripped = normalizeSubject(raw);
        return stripped.isBlank() ? raw : stripped;
    }

    /** 剥离回复/转发前缀（Re:/Fwd:/回复:/转发: 等，循环剥离链式前缀） */
    static String normalizeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        String cur = subject.trim();
        java.util.regex.Matcher m;
        while ((m = SUBJECT_REPLY_PREFIX.matcher(cur)).find()) {
            cur = cur.substring(m.end()).trim();
        }
        return cur;
    }

    private static String find(Map<String, String> parent, String x) {
        String p = parent.get(x);
        if (p == null || p.equals(x)) {
            return x;
        }
        String root = find(parent, p);
        parent.put(x, root);
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }

    /** 会话聚合中间态（线程归组 → 分页 → 响应装配用） */
    private record ConversationAggregate(String threadId, List<MailMessage> mails) {
        MailMessage latest() {
            return mails.get(0);
        }
    }

    /**
     * 系统级按 ID 查询邮件（不做用户归属校验）。
     *
     * <p>仅用于服务端跨用户代理场景（如 SO PDF 代理：授权锚点为 SO 记录本身，
     * 业务授权由调用方完成），禁止在面向当前用户的接口中替代 {@link #getOwned(String)}。
     *
     * <p>实现说明：必须走原生 SQL 而非 MetaQuery——MetaQuery 会被
     * PlatformFluentQueryFilterInjector 注入行级数据权限（追加 creator=当前用户），
     * 跨用户代理场景下邮件创建者非当前会话用户，记录被过滤不可见（2026-08-13 实测）；
     * 原生 SQL 不经该注入器。租户隔离显式保留（代理授权锚点 SO 记录限同租户）。
     */
    public MailMessage getByIdSystem(String id) {
        var nativeSql = MetaFactory.sql(
                "SELECT * FROM mail_message WHERE id = ? AND del_status = 0 AND tenant_code = ?");
        nativeSql.param(id);
        nativeSql.param(MailSessionCtx.getCurrentTenantCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // 原生 SQL 结果集列名为 snake_case（详见 folderCounts 注释），转 camelCase 复用 toEntity
        return toEntity(snakeKeysToCamel(rows.get(0)));
    }

    /**
     * 系统级按 RFC Message-ID 查邮件（识别推送桥接用，取 create_at 最新一封）。
     *
     * <p>实现说明：必须走原生 SQL 而非 MetaQuery——MetaQuery 会被
     * PlatformFluentQueryFilterInjector 注入行级数据权限（追加 creator=当前用户）。
     * 推送桥接场景下操作者（YeeForm 推送账户/admin）非邮件创建者，MetaQuery 过滤后
     * 记录不可见（2026-08-15 实测：admin 推送 → creator=admin 过滤 → gl_user 邮件
     * 查不到 → 桥接误报「邮件尚未同步到本系统」）；原生 SQL 不经该注入器。
     * 租户隔离不显式过滤——桥接以 il_so_file.mail_message_id 为锚点跨用户履行任务，
     * 邮件属主由任务 userId 体现（多账号同 Message-ID 场景取最新一封即可）。
     */
    public MailMessage getByMessageIdSystem(String messageId) {
        var nativeSql = MetaFactory.sql(
                "SELECT * FROM mail_message WHERE message_id = ? AND del_status = 0 "
                        + "ORDER BY create_at DESC LIMIT 1");
        nativeSql.param(messageId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(snakeKeysToCamel(rows.get(0)));
    }

    /** 原生 SQL 结果集键名 snake_case → camelCase（{@link #toEntity} 以 camelCase 取值的约定） */
    private static Map<String, Object> snakeKeysToCamel(Map<String, Object> row) {
        Map<String, Object> camel = new HashMap<>((int) (row.size() / 0.75) + 1);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            StringBuilder sb = new StringBuilder(key.length());
            boolean upperNext = false;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c == '_') {
                    upperNext = true;
                } else {
                    sb.append(upperNext ? Character.toUpperCase(c) : c);
                    upperNext = false;
                }
            }
            camel.put(sb.toString(), entry.getValue());
        }
        return camel;
    }

    /**
     * 文件夹未读/总数聚合（GET /folders 用）。
     * 使用原生 SQL 一次聚合（参数绑定防注入），避免逐文件夹多次 count。
     *
     * @return key=文件夹/虚拟视图，value=[unread,total]
     */
    public Map<String, long[]> folderCounts(String accountId) {
        String userId = MailSessionCtx.getCurrentUserId();
        Map<String, long[]> counts = new HashMap<>();
        StringBuilder sql = new StringBuilder(
                // 原生 SQL 结果集列名不做蛇形转驼峰（MetaFactory nativeSql.list() 原样返回列名），
                // 故多词聚合列必须 AS 驼峰别名，否则 row.get("starredTotal") 恒 null（ST-9 上报，ST-10 实证修复）
                "SELECT folder AS f, SUM(read_status = 'unread') AS unread, COUNT(*) AS total, "
                        + "SUM(flags_json LIKE '%\"starred\"%') AS starredTotal, "
                        + "SUM(flags_json LIKE '%\"starred\"%' AND read_status = 'unread') AS starredUnread, "
                        + "SUM(flags_json LIKE '%\"todo\"%') AS todoTotal, "
                        + "SUM(flags_json LIKE '%\"todo\"%' AND read_status = 'unread') AS todoUnread "
                        + "FROM mail_message WHERE user_id = ? AND del_status = 0");
        List<Object> sqlParams = new ArrayList<>();
        sqlParams.add(userId);
        if (accountId != null && !accountId.isBlank()) {
            sql.append(" AND account_id = ?");
            sqlParams.add(accountId);
        }
        sql.append(" GROUP BY folder");
        var nativeSql = MetaFactory.sql(sql.toString());
        sqlParams.forEach(nativeSql::param);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        long starredTotal = 0;
        long starredUnread = 0;
        long todoTotal = 0;
        long todoUnread = 0;
        for (Map<String, Object> row : rows) {
            String folder = str(row.get("f"));
            long unread = longVal(row.get("unread"));
            long total = longVal(row.get("total"));
            if (folder != null) {
                counts.put(folder, new long[]{unread, total});
            }
            starredTotal += longVal(row.get("starredTotal"));
            starredUnread += longVal(row.get("starredUnread"));
            todoTotal += longVal(row.get("todoTotal"));
            todoUnread += longVal(row.get("todoUnread"));
        }
        counts.put("starred", new long[]{starredUnread, starredTotal});
        counts.put("todo", new long[]{todoUnread, todoTotal});
        return counts;
    }

    // ==================== 同步落库 ====================

    /** 已同步的 IMAP UID 集合（同步去重依据，accountId + inbox 范围） */
    public Set<String> listExistingUids(String accountId) {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .select(new String[]{"imapUid"})
                .where(Filter.eq("accountId", accountId),
                        Filter.eq("folder", "inbox"),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        Set<String> uids = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String uid = str(row.get("imapUid"));
            if (uid != null) {
                uids.add(uid);
            }
        }
        return uids;
    }

    /** 同步落库单封邮件（调用方负责去重判断），返回已落库实体（含 id 回填，供收信过滤器钩子使用） */
    public MailMessage saveIncoming(MailAccount account, MailProtocolService.ParsedMail parsed) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailMessage msg = new MailMessage();
        msg.setAccountId(account.getId());
        msg.setUserId(userId);
        msg.setMessageId(parsed.getMessageId());
        msg.setInReplyTo(parsed.getInReplyTo());
        msg.setImapUid(parsed.getImapUid());
        msg.setFolder("inbox");
        msg.setSubject(parsed.getSubject());
        msg.setFromName(parsed.getFromName());
        msg.setFromEmail(parsed.getFromEmail() == null ? "" : parsed.getFromEmail());
        msg.setToJson(parsed.getToJson());
        msg.setCcJson(parsed.getCcJson());
        msg.setPreview(parsed.getPreview());
        msg.setContentHtml(parsed.getContentHtml());
        msg.setContentText(parsed.getContentText());
        msg.setSendDate(parsed.getSendDate() == null ? now : parsed.getSendDate());
        msg.setReadStatus(parsed.getReadStatus() == null ? "unread" : parsed.getReadStatus());
        msg.setFlagsJson(parsed.isStarred() ? "[\"starred\"]" : "[]");
        msg.setMailSize(parsed.getMailSize());
        msg.setHasAttachment(parsed.getHasAttachment());
        msg.setAttachmentsJson(parsed.getAttachmentsJson());
        msg.setPriority("normal");
        msg.setIsDraft(0);
        fillAudit(msg, userId, userName, now);
        Map<String, Object> saved = dynamicDao.save(msg);
        if (msg.getId() == null && saved != null && saved.get("id") != null) {
            msg.setId(String.valueOf(saved.get("id")));
        }
        return msg;
    }

    /** 最新一封收件箱邮件（通知端点 latestMail 用；无则 null） */
    public MailMessage findLatestInbox() {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("folder", "inbox"),
                        Filter.eq("isDraft", 0),
                        Filter.eq("delStatus", 0))
                .order(Order.desc("sendDate"));
        PageResult<Map<String, Object>> pageResult = query.page(1, 1).page();
        if (pageResult.getRecords() == null || pageResult.getRecords().isEmpty()) {
            return null;
        }
        return toEntity(pageResult.getRecords().get(0));
    }

    // ==================== 发送 ====================

    /**
     * SMTP 发送成功后保存发件箱副本，返回副本记录 id。
     *
     * @param compose 写信数据（to/subject/content 等）
     * @param smtpMessageId SMTP 服务端分配的 Message-ID（可空）
     */
    public String saveSentCopy(MailAccount account, ComposeRequest compose, String smtpMessageId) {
        return doSaveSentCopy(account, compose, smtpMessageId, "sent", null);
    }

    /**
     * SMTP 发送失败后留痕发件箱失败副本（V76：send_status='failed' + send_error 摘要），
     * 返回副本记录 id。诚实暴露失败事实，同时在发件箱可见、可供 bg-send 状态查询回读。
     */
    public String saveFailedCopy(MailAccount account, ComposeRequest compose, String errorSummary) {
        return doSaveSentCopy(account, compose, null, "failed", errorSummary);
    }

    /** 发件箱副本单次落库（成功/失败共用；失败副本无 SMTP Message-ID） */
    private String doSaveSentCopy(MailAccount account, ComposeRequest compose, String smtpMessageId,
                                  String sendStatus, String sendError) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailMessage msg = new MailMessage();
        msg.setAccountId(account.getId());
        msg.setUserId(userId);
        msg.setMessageId(smtpMessageId);
        msg.setInReplyTo(compose.getInReplyTo());
        msg.setFolder("sent");
        msg.setSubject(compose.getSubject());
        msg.setFromName(account.getName());
        msg.setFromEmail(account.getEmail());
        msg.setToJson(addressesToJson(compose.getTo()));
        msg.setCcJson(addressesToJson(compose.getCc()));
        msg.setBccJson(addressesToJson(compose.getBcc()));
        String html = compose.getContent() == null ? "" : compose.getContent();
        msg.setContentHtml(html);
        msg.setPreview(stripHtml(html, 200));
        msg.setSendDate(now);
        msg.setReadStatus("read");
        msg.setFlagsJson("[]");
        msg.setMailSize(html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        int hasAttachment = compose.getAttachments() != null && !compose.getAttachments().isEmpty() ? 1 : 0;
        msg.setHasAttachment(hasAttachment);
        msg.setAttachmentsJson(composeAttachmentsToJson(compose.getAttachments()));
        msg.setPriority(compose.getPriority() == null ? "normal" : compose.getPriority());
        msg.setIsDraft(0);
        msg.setSendStatus(sendStatus);
        msg.setSendError(sendError == null ? null
                : sendError.substring(0, Math.min(500, sendError.length())));
        fillAudit(msg, userId, userName, now);
        Map<String, Object> saved = dynamicDao.save(msg);
        if (msg.getId() == null && saved != null && saved.get("id") != null) {
            msg.setId(String.valueOf(saved.get("id")));
        }
        return msg.getId();
    }

    /** 撤回失败留痕（V76：withdraw_status='failed'；SMTP 无真实撤回能力，仅记录尝试） */
    public void markWithdrawFailed(MailMessage msg) {
        msg.setWithdrawStatus("failed");
        msg.setUpdateAt(new Date());
        msg.setUpdater(MailSessionCtx.getCurrentUserId());
        msg.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(msg);
    }

    // ==================== 草稿（V75：folder='draft' + is_draft=1 的 mail_message 记录） ====================

    /**
     * 新建草稿，返回草稿记录 id。
     *
     * <p>草稿不建独立表：前端契约经 GET /list?folder=draft 列表、GET /{id} 打开、
     * MailItem.isDraft 识别，与主流 Webmail（Drafts 文件夹存消息）一致。
     * 扩展字段（inReplyTo/signatureId/scheduleSendAt 等）落 draft_ext_json。
     */
    public String saveDraft(MailAccount account, ComposeRequest compose) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailMessage msg = new MailMessage();
        msg.setAccountId(account.getId());
        msg.setUserId(userId);
        msg.setFolder("draft");
        msg.setFromName(account.getName());
        msg.setFromEmail(account.getEmail());
        msg.setSendDate(now);
        msg.setReadStatus("read");
        msg.setFlagsJson("[]");
        msg.setIsDraft(1);
        fillDraftFields(msg, compose);
        fillAudit(msg, userId, userName, now);
        Map<String, Object> saved = dynamicDao.save(msg);
        if (msg.getId() == null && saved != null && saved.get("id") != null) {
            msg.setId(String.valueOf(saved.get("id")));
        }
        return msg.getId();
    }

    /** 查询当前用户草稿（folder='draft' 且 is_draft=1；越权/不存在/非草稿返回 null） */
    public MailMessage getOwnedDraft(String id) {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("id", id),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("folder", "draft"),
                        Filter.eq("isDraft", 1),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    /** 更新草稿（全量覆盖写字段；前端始终提交完整写信表单状态） */
    public void updateDraft(MailMessage draft, MailAccount account, ComposeRequest compose) {
        draft.setAccountId(account.getId());
        draft.setFromName(account.getName());
        draft.setFromEmail(account.getEmail());
        fillDraftFields(draft, compose);
        draft.setUpdateAt(new Date());
        draft.setUpdater(MailSessionCtx.getCurrentUserId());
        draft.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(draft);
    }

    /** 逻辑删除草稿（发送成功后清理/用户主动删除） */
    public void deleteDraft(MailMessage draft) {
        Date now = new Date();
        draft.setDelStatus(1);
        draft.setDeleteAt(now);
        draft.setUpdateAt(now);
        draft.setUpdater(MailSessionCtx.getCurrentUserId());
        draft.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(draft);
    }

    /** 更新用户备注（V75：PATCH /{id}/note；空串清除备注） */
    public void updateNote(MailMessage msg, String note) {
        msg.setNote(note == null || note.isBlank() ? null : note.trim());
        msg.setUpdateAt(new Date());
        msg.setUpdater(MailSessionCtx.getCurrentUserId());
        msg.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(msg);
    }

    /** 草稿字段全量映射（save/update 共用；content 为空串时预览/大小同步归零） */
    private void fillDraftFields(MailMessage msg, ComposeRequest compose) {
        msg.setSubject(compose.getSubject());
        msg.setToJson(addressesToJson(compose.getTo()));
        msg.setCcJson(addressesToJson(compose.getCc()));
        msg.setBccJson(addressesToJson(compose.getBcc()));
        String html = compose.getContent() == null ? "" : compose.getContent();
        msg.setContentHtml(html);
        msg.setPreview(stripHtml(html, 200));
        msg.setMailSize(html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        int hasAttachment = compose.getAttachments() != null && !compose.getAttachments().isEmpty() ? 1 : 0;
        msg.setHasAttachment(hasAttachment);
        msg.setAttachmentsJson(composeAttachmentsToJson(compose.getAttachments()));
        msg.setPriority(compose.getPriority() == null ? "normal" : compose.getPriority());
        msg.setDraftExtJson(buildDraftExtJson(compose));
    }

    /** 构建草稿扩展 JSON（仅白名单 key 且非空值；encryptPassword 等敏感字段禁入） */
    private String buildDraftExtJson(ComposeRequest compose) {
        Map<String, Object> ext = new LinkedHashMap<>();
        putIfNotNull(ext, "inReplyTo", compose.getInReplyTo());
        putIfNotNull(ext, "forwardFrom", compose.getForwardFrom());
        putIfNotNull(ext, "forwardAsAttachment", compose.getForwardAsAttachment());
        putIfNotNull(ext, "signatureId", compose.getSignatureId());
        putIfNotNull(ext, "isSeparateSend", compose.getIsSeparateSend());
        putIfNotNull(ext, "scheduleSendAt", compose.getScheduleSendAt());
        putIfNotNull(ext, "isEncrypted", compose.getIsEncrypted());
        putIfNotNull(ext, "requestReadReceipt", compose.getRequestReadReceipt());
        return ext.isEmpty() ? null : writeJson(ext);
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    // ==================== 批量操作 ====================

    /**
     * 批量操作（read/unread/star/unstar/todo/untodo/markTodoDone/delete/move/archive/unarchive/markSpam/markNotSpam/setLabels）。
     *
     * <p>setLabels（V75 新增，邮件-标签关联写操作）：target 为逗号分隔的标签ID列表，
     * 空 target 表示清空标签；写入前校验标签归属当前用户。
     *
     * @return 实际处理的记录数
     */
    public int batch(List<String> ids, String op, String target) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> labelIds = null;
        if ("setLabels".equals(op)) {
            labelIds = parseLabelIdTarget(target);
            // 归属校验：存在越权/不存在的标签时 fail-fast，不写任何邮件
            Map<String, MailLabel> owned = labelService.mapByIds(labelIds);
            for (String labelId : labelIds) {
                if (!owned.containsKey(labelId)) {
                    throw new IllegalArgumentException("标签不存在或不属于当前用户: " + labelId);
                }
            }
        }
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.in("id", ids.toArray()),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        Date now = new Date();
        int affected = 0;
        for (Map<String, Object> row : rows) {
            MailMessage msg = toEntity(row);
            applyOp(msg, op, target, now);
            msg.setUpdateAt(now);
            msg.setUpdater(userId);
            msg.setUpdaterName(userName);
            dynamicDao.save(msg);
            affected++;
        }
        return affected;
    }

    /** 解析 setLabels 的 target（逗号分隔标签ID，去空白去重保持顺序） */
    private List<String> parseLabelIdTarget(String target) {
        List<String> labelIds = new ArrayList<>();
        if (target == null || target.isBlank()) {
            return labelIds;
        }
        for (String part : target.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !labelIds.contains(id)) {
                labelIds.add(id);
            }
        }
        return labelIds;
    }

    private void applyOp(MailMessage msg, String op, String target, Date now) {
        switch (op) {
            case "read" -> msg.setReadStatus("read");
            case "unread" -> msg.setReadStatus("unread");
            case "star" -> msg.setFlagsJson(addFlag(msg.getFlagsJson(), "starred"));
            case "unstar" -> msg.setFlagsJson(removeFlag(msg.getFlagsJson(), "starred"));
            case "todo" -> msg.setFlagsJson(addFlag(msg.getFlagsJson(), "todo"));
            case "untodo" -> msg.setFlagsJson(removeFlag(msg.getFlagsJson(), "todo"));
            case "markTodoDone" -> msg.setFlagsJson(
                    addFlag(removeFlag(msg.getFlagsJson(), "todo"), "todoDone"));
            case "delete" -> {
                if ("trash".equals(msg.getFolder())) {
                    // 回收站再删 = 逻辑删除
                    msg.setDelStatus(1);
                    msg.setDeleteAt(now);
                } else {
                    msg.setFolder("trash");
                }
            }
            case "move" -> {
                if (isValidTargetFolder(target)) {
                    msg.setFolder(target);
                }
            }
            case "archive" -> msg.setFolder("archive");
            case "unarchive" -> msg.setFolder("inbox");
            case "markSpam" -> msg.setFolder("spam");
            case "markNotSpam" -> msg.setFolder("inbox");
            case "setLabels" -> msg.setLabelIds(writeIdArray(parseLabelIdTarget(target)));
            default -> throw new IllegalArgumentException("不支持的批量操作: " + op);
        }
    }

    private boolean isValidTargetFolder(String target) {
        if (target == null) {
            return false;
        }
        if (PHYSICAL_FOLDERS.contains(target)) {
            return true;
        }
        return target.startsWith("custom_") && target.length() > "custom_".length();
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailItem 契约（详情用：单封解析标签） */
    public Map<String, Object> toResponse(MailMessage msg) {
        return toResponse(msg, labelService.mapByIds(parseIdArray(msg.getLabelIds())));
    }

    /** 转前端 MailItem 契约（列表用：标签批量解析结果传入，避免 N+1） */
    public Map<String, Object> toResponse(MailMessage msg, Map<String, MailLabel> labelMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", msg.getId());
        map.put("messageId", msg.getMessageId() != null ? msg.getMessageId() : msg.getId());
        map.put("folder", msg.getFolder());
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("name", msg.getFromName() != null ? msg.getFromName() : msg.getFromEmail());
        from.put("email", msg.getFromEmail());
        map.put("from", from);
        map.put("to", parseAddresses(msg.getToJson()));
        List<Map<String, Object>> cc = parseAddresses(msg.getCcJson());
        if (!cc.isEmpty()) {
            map.put("cc", cc);
        }
        List<Map<String, Object>> bcc = parseAddresses(msg.getBccJson());
        if (!bcc.isEmpty()) {
            map.put("bcc", bcc);
        }
        map.put("subject", msg.getSubject() == null ? "" : msg.getSubject());
        map.put("preview", msg.getPreview() == null ? "" : msg.getPreview());
        String content = msg.getContentHtml() != null ? msg.getContentHtml()
                : msg.getContentText() != null ? msg.getContentText() : "";
        map.put("content", content);
        map.put("attachments", parseAttachments(msg.getId(), msg.getAttachmentsJson()));
        map.put("date", msg.getSendDate() == null ? "" : msg.getSendDate().toInstant().toString());
        map.put("readStatus", msg.getReadStatus());
        map.put("flags", deriveFlags(msg));
        map.put("size", msg.getMailSize());
        map.put("isDraft", msg.getIsDraft() == 1);
        if (msg.getIsDraft() == 1) {
            map.put("draftStatus", "draft");
            mergeDraftExt(map, msg.getDraftExtJson());
        }
        map.put("priority", msg.getPriority() == null ? "normal" : msg.getPriority());
        // V76：撤回/发送状态透传（前端撤回按钮状态机 / 发送失败展示；null 时不输出保持契约最小化）
        if (msg.getWithdrawStatus() != null) {
            map.put("withdrawStatus", msg.getWithdrawStatus());
        }
        if (msg.getSendStatus() != null) {
            map.put("sendStatus", msg.getSendStatus());
        }
        if (msg.getSendError() != null) {
            map.put("sendError", msg.getSendError());
        }
        List<String> flags = parseFlags(msg.getFlagsJson());
        map.put("isTodoDone", flags.contains("todoDone"));
        map.put("isArchived", "archive".equals(msg.getFolder()));
        if (msg.getNote() != null) {
            map.put("note", msg.getNote());
        }
        List<String> labelIds = parseIdArray(msg.getLabelIds());
        if (!labelIds.isEmpty()) {
            List<Map<String, Object>> labels = new ArrayList<>();
            for (String labelId : labelIds) {
                MailLabel label = labelMap.get(labelId);
                if (label != null) {
                    labels.add(labelService.toEmbeddedResponse(label));
                }
            }
            if (!labels.isEmpty()) {
                map.put("labels", labels);
            }
        }
        return map;
    }

    /** 草稿扩展字段合并进响应（写信页恢复用；仅存白名单 key，忽略未知字段） */
    private void mergeDraftExt(Map<String, Object> map, String draftExtJson) {
        if (draftExtJson == null || draftExtJson.isBlank()) {
            return;
        }
        Map<String, Object> ext;
        try {
            ext = MAPPER.readValue(draftExtJson,
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("草稿扩展 JSON 解析失败: " + draftExtJson, e);
        }
        for (String key : DRAFT_EXT_KEYS) {
            Object value = ext.get(key);
            if (value != null) {
                map.put(key, value);
            }
        }
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    MailMessage toEntity(Map<String, Object> row) {
        MailMessage msg = new MailMessage();
        msg.setId(str(row.get("id")));
        msg.setAccountId(str(row.get("accountId")));
        msg.setUserId(str(row.get("userId")));
        msg.setMessageId(str(row.get("messageId")));
        msg.setInReplyTo(str(row.get("inReplyTo")));
        msg.setImapUid(str(row.get("imapUid")));
        msg.setFolder(str(row.get("folder")));
        msg.setSubject(str(row.get("subject")));
        msg.setFromName(str(row.get("fromName")));
        msg.setFromEmail(str(row.get("fromEmail")));
        msg.setToJson(str(row.get("toJson")));
        msg.setCcJson(str(row.get("ccJson")));
        msg.setBccJson(str(row.get("bccJson")));
        msg.setPreview(str(row.get("preview")));
        msg.setContentHtml(str(row.get("contentHtml")));
        msg.setContentText(str(row.get("contentText")));
        msg.setSendDate(toDate(row.get("sendDate")));
        msg.setReadStatus(str(row.get("readStatus")));
        msg.setFlagsJson(str(row.get("flagsJson")));
        msg.setMailSize(intVal(row.get("mailSize")));
        msg.setHasAttachment(intVal(row.get("hasAttachment")));
        msg.setAttachmentsJson(str(row.get("attachmentsJson")));
        msg.setLabelIds(str(row.get("labelIds")));
        msg.setDraftExtJson(str(row.get("draftExtJson")));
        msg.setSendStatus(str(row.get("sendStatus")));
        msg.setSendError(str(row.get("sendError")));
        msg.setWithdrawStatus(str(row.get("withdrawStatus")));
        msg.setNote(str(row.get("note")));
        msg.setPriority(str(row.get("priority")));
        msg.setIsDraft(intVal(row.get("isDraft")));
        msg.setTenantCode(str(row.get("tenantCode")));
        msg.setDelStatus(intVal(row.get("delStatus")));
        msg.setCreateAt(toDate(row.get("createAt")));
        msg.setUpdateAt(toDate(row.get("updateAt")));
        msg.setCreator(str(row.get("creator")));
        msg.setCreatorName(str(row.get("creatorName")));
        msg.setUpdater(str(row.get("updater")));
        msg.setUpdaterName(str(row.get("updaterName")));
        return msg;
    }

    /**
     * 平台 MetaQuery 行日期值转换：DATETIME 列经 MySQL 8 驱动实际返回 LocalDateTime，
     * 直接 instanceof Date 判定会静默丢值（曾致 withdraw 全量更新时 send_date 被写 null）。
     * 未知类型 fail-fast 抛异常，禁止静默置 null 掩盖类型漂移。
     */
    public static Date toDate(Object value) {
        if (value == null || value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof LocalDateTime ldt) {
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }
        throw new IllegalStateException("未知的日期类型: " + value.getClass().getName());
    }

    // ==================== 内部辅助 ====================

    /** 解析 ID JSON 数组字符串（label_ids 等；null/空白返回可变空列表，解析失败 fail-fast） */
    public static List<String> parseIdArray(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException("ID 数组 JSON 解析失败: " + json, e);
        }
    }

    /** 序列化 ID 列表为 JSON 数组字符串（null 视为空数组） */
    public static String writeIdArray(List<String> ids) {
        try {
            return MAPPER.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private void applyFolderFilter(List<Filter> filters, String folder) {
        if (folder == null || folder.isBlank() || "inbox".equals(folder)) {
            filters.add(Filter.eq("folder", "inbox"));
            return;
        }
        switch (folder) {
            case "starred" -> filters.add(Filter.like("flagsJson", "%\"starred\"%"));
            case "todo" -> filters.add(Filter.like("flagsJson", "%\"todo\"%"));
            case "important" ->
                // P2 重要联系人视图（依赖联系人模块）：P0 无匹配条件，返回空结果
                filters.add(Filter.eq("folder", "__none__"));
            default -> {
                if (PHYSICAL_FOLDERS.contains(folder) || folder.startsWith("custom_")) {
                    filters.add(Filter.eq("folder", folder));
                } else {
                    filters.add(Filter.eq("folder", "inbox"));
                }
            }
        }
    }

    private void applySearchFilters(List<Filter> filters, MailListParams params) {
        if (params.keyword != null && !params.keyword.isBlank()) {
            // Fluent DSL 扁平 FilterGroup 不支持括号 OR 嵌套，P0 裸词仅匹配 subject（P1 扩展多字段）
            filters.add(Filter.like("subject", "%" + escapeLike(params.keyword.trim()) + "%"));
        }
        if (params.from != null && !params.from.isBlank()) {
            filters.add(Filter.like("fromEmail", "%" + escapeLike(params.from.trim()) + "%"));
        }
        if (params.to != null && !params.to.isBlank()) {
            filters.add(Filter.like("toJson", "%" + escapeLike(params.to.trim()) + "%"));
        }
        if (params.subject != null && !params.subject.isBlank()) {
            filters.add(Filter.like("subject", "%" + escapeLike(params.subject.trim()) + "%"));
        }
        if (Boolean.TRUE.equals(params.isUnread)) {
            filters.add(Filter.eq("readStatus", "unread"));
        }
        if (Boolean.TRUE.equals(params.isStarred)) {
            filters.add(Filter.like("flagsJson", "%\"starred\"%"));
        }
        if (Boolean.TRUE.equals(params.hasAttachment)) {
            filters.add(Filter.eq("hasAttachment", 1));
        }
        if (params.labelId != null && !params.labelId.isBlank()) {
            // 标签过滤：label_ids 为 JSON 数组字符串，按带引号的精确 id 片段匹配
            filters.add(Filter.like("labelIds", "%\"" + params.labelId.trim() + "\"%"));
        }
        Date from = parseIsoDate(params.dateFrom);
        if (from != null) {
            filters.add(Filter.ge("sendDate", from));
        }
        Date to = parseIsoDate(params.dateTo);
        if (to != null) {
            filters.add(Filter.le("sendDate", to));
        }
    }

    /** LIKE 通配符转义（%/_ 前缀反斜杠，MySQL LIKE 默认转义符；与 MailContactService.escapeLike 同口径） */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Order resolveOrder(String sortField, String sortOrder) {
        String column = switch (sortField == null ? "" : sortField) {
            case "subject" -> "subject";
            case "from" -> "fromEmail";
            default -> "sendDate";
        };
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        return asc ? Order.asc(column) : Order.desc(column);
    }

    private List<String> deriveFlags(MailMessage msg) {
        List<String> flags = new ArrayList<>(parseFlags(msg.getFlagsJson()));
        if ("unread".equals(msg.getReadStatus()) && !flags.contains("unread")) {
            flags.add("unread");
        }
        if (msg.getHasAttachment() == 1 && !flags.contains("attachment")) {
            flags.add("attachment");
        }
        return flags;
    }

    private List<String> parseFlags(String flagsJson) {
        if (flagsJson == null || flagsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(flagsJson,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException("邮件标记 JSON 解析失败: " + flagsJson, e);
        }
    }

    private String addFlag(String flagsJson, String flag) {
        List<String> flags = parseFlags(flagsJson);
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
        return writeJson(flags);
    }

    private String removeFlag(String flagsJson, String flag) {
        List<String> flags = parseFlags(flagsJson);
        flags.remove(flag);
        return writeJson(flags);
    }

    private List<Map<String, Object>> parseAddresses(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<Map<String, Object>> list = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            for (Map<String, Object> item : list) {
                Map<String, Object> addr = new LinkedHashMap<>();
                Object email = item.get("email");
                addr.put("name", item.get("name") != null ? String.valueOf(item.get("name")) : String.valueOf(email));
                addr.put("email", email == null ? "" : String.valueOf(email));
                result.add(addr);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("邮件地址 JSON 解析失败: " + json, e);
        }
    }

    private List<Map<String, Object>> parseAttachments(String mailId, String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<Map<String, Object>> list = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            int idx = 0;
            for (Map<String, Object> item : list) {
                Map<String, Object> att = new LinkedHashMap<>();
                String name = item.get("name") != null ? String.valueOf(item.get("name")) : "attachment";
                att.put("id", idx);
                att.put("name", name);
                att.put("size", item.get("size") instanceof Number n ? n.longValue() : 0L);
                att.put("type", mapAttachmentType(String.valueOf(item.get("contentType")), name));
                // P1 附件下载端点：GET /api/mail/{mailId}/attachments/{index}（本地 token 优先，IMAP 回源兜底）
                att.put("url", "/api/mail/" + mailId + "/attachments/" + idx);
                // 本地已落盘附件（写信真实上传）携带 token，再次编辑/重发时可定位文件
                if (item.get("token") != null) {
                    att.put("token", String.valueOf(item.get("token")));
                }
                att.put("status", "uploaded");
                result.add(att);
                idx++;
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("附件元数据 JSON 解析失败: " + json, e);
        }
    }

    private String mapAttachmentType(String contentType, String name) {
        return MailMimeSupport.attachmentType(contentType, name);
    }

    private String addressesToJson(List<AddressDto> addresses) {
        if (addresses == null) {
            return null;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (AddressDto a : addresses) {
            if (a == null || a.getEmail() == null || a.getEmail().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", a.getName() != null ? a.getName() : a.getEmail());
            item.put("email", a.getEmail());
            list.add(item);
        }
        return writeJson(list);
    }

    /** 附件元数据原始列表（附件下载端点用：name/size/contentType/token 原样透出） */
    public List<Map<String, Object>> attachmentMetadata(MailMessage msg) {
        String json = msg.getAttachmentsJson();
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new IllegalStateException("附件元数据 JSON 解析失败: " + json, e);
        }
    }

    private String composeAttachmentsToJson(List<AttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (AttachmentDto a : attachments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", a.getName());
            item.put("size", a.getSize());
            // contentType 优先取上传回传的真实 MIME 类型（P1 真实上传），
            // 缺省回退前端 type 枚举值（向后兼容旧契约；下载时非 MIME 值按 octet-stream 处理）
            item.put("contentType", a.getContentType() != null && !a.getContentType().isBlank()
                    ? a.getContentType() : a.getType());
            if (a.getToken() != null && !a.getToken().isBlank()) {
                item.put("token", a.getToken());
            }
            list.add(item);
        }
        return writeJson(list);
    }

    private String stripHtml(String html, int maxLen) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.substring(0, Math.min(maxLen, text.length()));
    }

    private Date parseIsoDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Date.from(java.time.Instant.parse(text.trim()));
        } catch (Exception e) {
            try {
                return Date.from(java.time.LocalDate.parse(text.trim())
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private void fillAudit(MailMessage msg, String userId, String userName, Date now) {
        msg.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        msg.setDelStatus(0);
        msg.setCreateAt(now);
        msg.setUpdateAt(now);
        msg.setCreator(userId);
        msg.setCreatorName(userName);
        msg.setUpdater(userId);
        msg.setUpdaterName(userName);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private long longVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }

    // ==================== DTO ====================

    /** 列表查询参数（Controller 从 query string 解析填充） */
    @lombok.Data
    public static class MailListParams {
        private String accountId;
        private String folder = "inbox";
        private int page = 1;
        private int pageSize = 20;
        private String keyword;
        private String from;
        private String to;
        private String subject;
        private Boolean isUnread;
        private Boolean isStarred;
        private Boolean hasAttachment;
        private String labelId;
        private String dateFrom;
        private String dateTo;
        private String sortField;
        private String sortOrder;
        /** P1-F4: viewMode=list|conversation（缺省 list） */
        private String viewMode;
    }

    /** 写信请求（POST /send 与草稿共用，字段与前端 MailCompose 对齐） */
    @lombok.Data
    public static class ComposeRequest {
        private List<AddressDto> to;
        private List<AddressDto> cc;
        private List<AddressDto> bcc;
        private String subject;
        private String content;
        private List<AttachmentDto> attachments;
        private String fromAccountId;
        private String priority;
        // 草稿扩展字段（V75：落 draft_ext_json，写信页恢复用；/send 忽略）
        private String inReplyTo;
        private String forwardFrom;
        private String forwardAsAttachment;
        private String signatureId;
        private Boolean isSeparateSend;
        private String scheduleSendAt;
        private Boolean isEncrypted;
        private Boolean requestReadReceipt;
    }

    @lombok.Data
    public static class AddressDto {
        private String name;
        private String email;
    }

    @lombok.Data
    public static class AttachmentDto {
        private String name;
        private Long size;
        /** 前端类型枚举（image/pdf/doc/xls/zip/other）；真实 MIME 类型走 contentType */
        private String type;
        /** 真实 MIME Content-Type（POST /attachments/upload 响应回传；发送/下载响应头依据） */
        private String contentType;
        /** 附件上传 token（POST /attachments/upload 签发；V76 真实落盘附件定位依据） */
        private String token;
    }
}
