package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailFilter;
import cn.geelato.mail.entity.MailFilterApplyLog;
import cn.geelato.mail.entity.MailLabel;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.util.MailSessionCtx;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件过滤器服务：CRUD + 批量排序 + 匹配引擎 + 手动 apply-existing + 收信钩子。
 *
 * <p>匹配语义（保存时不校验可匹配性，仅校验结构）：
 * <ul>
 *   <li>条件数组为 AND 语义（全部命中才算匹配；空条件数组不匹配任何邮件——安全默认，
 *       防止「无条件 + delete 动作」误清空收件箱）。mock 的 OR 语义为简化实现，非契约。</li>
 *   <li>field=from/to/subject/body 为文本匹配（大小写不敏感），operator 支持
 *       contains/notContains/equals/startsWith/endsWith；gt/lt 对文本字段不命中。</li>
 *   <li>field=size 按 mail_size 字节数数值比较（gt/lt/equals），value 必须为数字
 *       （保存时校验 fail-fast）；其余 operator 不命中。</li>
 *   <li>field=attachment 按 has_attachment 布尔比较（equals → value 解析为 true/1/yes
 *       时匹配含附件邮件）；其余 operator 不命中。</li>
 *   <li>from 同时匹配 from_email 与 from_name；to 同时匹配 to/cc 地址的 email 与 name；
 *       body 匹配 content_text + preview。</li>
 * </ul>
 *
 * <p>动作语义（apply-existing / 收信钩子共用）：
 * move（物理文件夹或 custom_*）/label（标签ID，归属校验）/markRead/markStar/archive/delete
 * （回收站语义与批量操作一致：非回收站邮件移入 trash）。folder 类动作 move/archive/delete
 * 互斥（保存时校验 fail-fast）。autoReply 动作在收信钩子中经 {@link MailAutoReplyService}
 * SMTP 真实发出（同发件人 24h 限一次）；手动 apply-existing 不触发 autoReply
 * （对存量邮件批量回复发件人属骚扰行为，仅对「新到」邮件回复是行业标准语义）。
 *
 * <p>apply-existing 范围：当前用户 folder='inbox' 且非草稿的既有邮件（契约语义「对既有
 * 收件箱邮件应用」）；执行前做动作预校验（label 归属/move 目标合法），预校验失败
 * fail-fast 40000 且不写任何邮件。每次手动应用写 mail_filter_apply_log 历史。
 *
 * <p>收信钩子（POST /mail/sync 触发）：对本次同步新落库的收件箱邮件，按 sortOrder 升序
 * 依次执行全部启用的过滤器（多条命中累积生效）；不写 apply 历史（高频避免膨胀）。
 * 过滤器执行完毕后由 {@link MailAutoReplyService#sendVacationReplyBatch} 统一处理
 * 假期自动回复（配置启用 + 时间窗内才触发）。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除/应用做归属校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Slf4j
@Service
public class MailFilterService {

    /** 条件字段白名单（前端 MailFilterField） */
    private static final Set<String> FIELDS = Set.of("from", "to", "subject", "body", "attachment", "size");
    /** 条件运算符白名单（前端 MailFilterCondition.operator） */
    private static final Set<String> OPERATORS = Set.of(
            "contains", "notContains", "equals", "startsWith", "endsWith", "gt", "lt");
    /** 动作键白名单（前端 MailFilterAction） */
    private static final Set<String> ACTION_KEYS = Set.of(
            "move", "label", "markRead", "markStar", "archive", "delete", "autoReply");
    /** 物理文件夹（与 MailMessageService 同口径；custom_* 单独匹配） */
    private static final Set<String> PHYSICAL_FOLDERS = Set.of(
            "inbox", "sent", "draft", "trash", "spam", "archive");
    /** 列宽上限（对齐 V79） */
    static final int MAX_NAME_LEN = 128;
    static final int MAX_CONDITION_VALUE_LEN = 512;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailLabelService labelService;

    @Autowired
    private MailAutoReplyService autoReplyService;

    // ==================== 查询 ====================

    /** 当前用户过滤器列表（sortOrder 升序，同序按创建时间） */
    public List<Map<String, Object>> list() {
        return listEntities().stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** 当前用户过滤器实体列表（public 供收信钩子/单测 stub） */
    public List<MailFilter> listEntities() {
        MetaQuery query = MetaFactory.query(MailFilter.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("sortOrder"), Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    public MailFilter getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailFilter.class)
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

    /** 指定过滤器的应用历史（手动 apply-existing；按应用时间倒序） */
    public List<Map<String, Object>> applyHistory(String filterId) {
        MetaQuery query = MetaFactory.query(MailFilterApplyLog.class)
                .where(Filter.eq("filterId", filterId),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.desc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(row.get("id")));
            // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
            Date createAt = MailMessageService.toDate(row.get("createAt"));
            item.put("appliedAt", createAt == null ? "" : createAt.toInstant().toString());
            Object count = row.get("appliedCount");
            item.put("appliedCount", count instanceof Number n ? n.intValue() : 0);
            item.put("appliedBy", str(row.get("appliedBy")) == null ? "" : str(row.get("appliedBy")));
            result.add(item);
        }
        return result;
    }

    // ==================== 写 ====================

    /**
     * 创建过滤器（sortOrder 缺省取当前用户最大值 + 1）。
     *
     * @throws IllegalArgumentException 结构/取值非法（调用方转 40000）
     */
    public MailFilter create(String name, Boolean enabled, List<Map<String, Object>> conditions,
                             Map<String, Object> action, Integer sortOrder, Boolean applyToExisting) {
        validateConditions(conditions);
        validateAction(action);
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailFilter filter = new MailFilter();
        filter.setUserId(userId);
        filter.setName(requireName(name));
        filter.setEnabled(Boolean.FALSE.equals(enabled) ? 0 : 1);
        filter.setConditionsJson(writeJson(conditions == null ? List.of() : conditions));
        filter.setActionJson(writeJson(action == null ? Map.of() : action));
        filter.setSortOrder(sortOrder == null ? nextSortOrder(userId) : sortOrder);
        filter.setApplyToExisting(Boolean.TRUE.equals(applyToExisting) ? 1 : 0);
        filter.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        filter.setDelStatus(0);
        filter.setCreateAt(now);
        filter.setUpdateAt(now);
        filter.setCreator(userId);
        filter.setCreatorName(userName);
        filter.setUpdater(userId);
        filter.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(filter);
        if (filter.getId() == null && saved != null && saved.get("id") != null) {
            filter.setId(String.valueOf(saved.get("id")));
        }
        return filter;
    }

    /** 局部更新过滤器（仅更新出现字段；conditions/action 出现即整体验证后替换） */
    public void update(MailFilter filter, String name, Boolean enabled,
                       List<Map<String, Object>> conditions, Map<String, Object> action,
                       Integer sortOrder, Boolean applyToExisting) {
        if (name != null) {
            filter.setName(requireName(name));
        }
        if (enabled != null) {
            filter.setEnabled(enabled ? 1 : 0);
        }
        if (conditions != null) {
            validateConditions(conditions);
            filter.setConditionsJson(writeJson(conditions));
        }
        if (action != null) {
            validateAction(action);
            filter.setActionJson(writeJson(action));
        }
        if (sortOrder != null) {
            filter.setSortOrder(sortOrder);
        }
        if (applyToExisting != null) {
            filter.setApplyToExisting(applyToExisting ? 1 : 0);
        }
        filter.setUpdateAt(new Date());
        filter.setUpdater(MailSessionCtx.getCurrentUserId());
        filter.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(filter);
    }

    /** 逻辑删除过滤器（应用历史保留，供审计回溯） */
    public void delete(MailFilter filter) {
        filter.setDelStatus(1);
        filter.setDeleteAt(new Date());
        filter.setUpdateAt(new Date());
        filter.setUpdater(MailSessionCtx.getCurrentUserId());
        filter.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(filter);
    }

    /**
     * 批量排序：按 ids 顺序将 sortOrder 重排为 1..n。
     *
     * @throws IllegalArgumentException ids 为空、含越权/不存在的过滤器 id（调用方转 40000）
     */
    public void reorder(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("排序ID列表不能为空");
        }
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        int order = 1;
        for (String id : ids) {
            MailFilter filter = id == null ? null : getOwned(id.trim());
            if (filter == null) {
                throw new IllegalArgumentException("过滤器不存在或不属于当前用户: " + id);
            }
            filter.setSortOrder(order++);
            filter.setUpdateAt(now);
            filter.setUpdater(userId);
            filter.setUpdaterName(userName);
            dynamicDao.save(filter);
        }
    }

    // ==================== 应用到既有邮件（手动） ====================

    /**
     * 手动应用过滤器到当前用户既有收件箱邮件（folder='inbox' 非草稿）。
     *
     * <p>动作预校验（label 归属/move 目标）失败 fail-fast 且不写任何邮件；
     * 每次应用写 mail_filter_apply_log（trigger_type='manual'）。
     *
     * @return 匹配并应用动作的邮件数
     */
    public int applyToExisting(MailFilter filter) {
        Map<String, Object> action = parseAction(filter.getActionJson());
        validateActionExecutable(filter, action);
        List<MailMessage> inbox = listInboxMessages();
        int applied = 0;
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        List<Map<String, Object>> conditions = parseConditions(filter.getConditionsJson());
        for (MailMessage msg : inbox) {
            if (!matches(msg, conditions)) {
                continue;
            }
            applyAction(msg, action, now, userId, userName);
            dynamicDao.save(msg);
            applied++;
        }
        writeApplyLog(filter.getId(), applied, userId, userName, now);
        return applied;
    }

    /**
     * 收信钩子：对同步新落库的收件箱邮件按 sortOrder 升序执行全部启用的过滤器。
     *
     * <p>命中过滤器的 autoReply 动作时经 {@link MailAutoReplyService#sendFilterReply}
     * SMTP 真实回复（同发件人同过滤器 24h 限一次）；全部过滤器执行完毕后经
     * {@link MailAutoReplyService#sendVacationReplyBatch} 处理假期自动回复。
     *
     * <p>辅助动作：单封/单条过滤器执行失败仅 warn 日志并继续，不影响同步主流程结果
     * （与 recordCompose 同口径的合法兜底；手动 apply-existing 不走此容错，fail-fast）。
     *
     * @param account 本次同步的邮箱账户（自动回复发件通道）
     * @param plainPassword 账户明文密码（SMTP 认证用，由调用方解密，不落库）
     */
    public void applyToIncoming(List<MailMessage> messages, MailAccount account, String plainPassword) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<MailFilter> enabled = listEntities().stream()
                .filter(f -> f.getEnabled() == 1)
                .collect(Collectors.toList());
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        for (MailMessage msg : messages) {
            for (MailFilter filter : enabled) {
                try {
                    List<Map<String, Object>> conditions = parseConditions(filter.getConditionsJson());
                    if (!matches(msg, conditions)) {
                        continue;
                    }
                    Map<String, Object> action = parseAction(filter.getActionJson());
                    applyAction(msg, action, new Date(), userId, userName);
                    // autoReply 真实发送（24h 频率上限/失败不阻断由 MailAutoReplyService 保证）
                    Object autoReply = action.get("autoReply");
                    if (autoReply != null && !String.valueOf(autoReply).isBlank()) {
                        autoReplyService.sendFilterReply(account, plainPassword, msg, filter,
                                String.valueOf(autoReply));
                    }
                } catch (RuntimeException e) {
                    // FALLBACK:[收信过滤器执行为同步辅助动作][单条失败仅日志并继续，不影响同步主流程]
                    log.warn("收信过滤器执行失败（filter={}, mail={}）: {}",
                            filter.getId(), msg.getId(), e.getMessage());
                }
            }
            dynamicDao.save(msg);
        }
        // 假期自动回复（配置启用 + 时间窗内才触发；服务内自校验）
        autoReplyService.sendVacationReplyBatch(account, plainPassword, messages);
    }

    // ==================== 匹配引擎 ====================

    /**
     * 条件匹配（AND 语义；空条件数组不匹配任何邮件——安全默认）。
     * public static 供单测直接覆盖匹配矩阵。
     */
    public static boolean matches(MailMessage msg, List<Map<String, Object>> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        for (Map<String, Object> condition : conditions) {
            if (!matchOne(msg, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchOne(MailMessage msg, Map<String, Object> condition) {
        Object field = condition.get("field");
        Object operator = condition.get("operator");
        Object value = condition.get("value");
        if (field == null || operator == null || value == null) {
            return false;
        }
        String f = String.valueOf(field);
        String op = String.valueOf(operator);
        String v = String.valueOf(value);
        return switch (f) {
            case "from" -> matchText(msg.getFromEmail() + " " + (msg.getFromName() == null ? "" : msg.getFromName()), op, v);
            case "to" -> matchText(addressesText(msg.getToJson()) + " " + addressesText(msg.getCcJson()), op, v);
            case "subject" -> matchText(msg.getSubject() == null ? "" : msg.getSubject(), op, v);
            case "body" -> matchText((msg.getContentText() == null ? "" : msg.getContentText())
                    + " " + (msg.getPreview() == null ? "" : msg.getPreview()), op, v);
            case "size" -> matchSize(msg.getMailSize(), op, v);
            case "attachment" -> matchAttachment(msg.getHasAttachment() == 1, op, v);
            default -> false;
        };
    }

    /** 文本匹配（大小写不敏感）；gt/lt 对文本字段不命中 */
    private static boolean matchText(String target, String op, String value) {
        String t = target.toLowerCase(Locale.ROOT);
        String v = value.toLowerCase(Locale.ROOT);
        return switch (op) {
            case "contains" -> t.contains(v);
            case "notContains" -> !t.contains(v);
            case "equals" -> t.equals(v);
            case "startsWith" -> t.startsWith(v);
            case "endsWith" -> t.endsWith(v);
            default -> false;
        };
    }

    /** 大小数值匹配（mail_size 字节）；非 gt/lt/equals 或 value 非数字不命中 */
    private static boolean matchSize(long mailSize, String op, String value) {
        long threshold;
        try {
            threshold = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (op) {
            case "gt" -> mailSize > threshold;
            case "lt" -> mailSize < threshold;
            case "equals" -> mailSize == threshold;
            default -> false;
        };
    }

    /** 附件布尔匹配（equals → value 为 true/1/yes 时匹配含附件邮件）；其余 operator 不命中 */
    private static boolean matchAttachment(boolean hasAttachment, String op, String value) {
        if (!"equals".equals(op)) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        boolean expected = "true".equals(v) || "1".equals(v) || "yes".equals(v);
        return hasAttachment == expected;
    }

    /** to/cc 地址 JSON 提取为可匹配文本（email + name 空格拼接） */
    private static String addressesText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            List<Map<String, Object>> list = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> item : list) {
                if (item.get("email") != null) {
                    sb.append(' ').append(item.get("email"));
                }
                if (item.get("name") != null) {
                    sb.append(' ').append(item.get("name"));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("邮件地址 JSON 解析失败: " + json, e);
        }
    }

    // ==================== 动作执行 ====================

    /** 在内存实体上应用动作（调用方负责 save）；autoReply 非邮件字段变更，由收信钩子单独发送 */
    private void applyAction(MailMessage msg, Map<String, Object> action,
                             Date now, String userId, String userName) {
        if (Boolean.TRUE.equals(action.get("markRead"))) {
            msg.setReadStatus("read");
        }
        if (Boolean.TRUE.equals(action.get("markStar"))) {
            List<String> flags = MailMessageService.parseIdArray(msg.getFlagsJson());
            if (!flags.contains("starred")) {
                flags.add("starred");
                msg.setFlagsJson(MailMessageService.writeIdArray(flags));
            }
        }
        if (Boolean.TRUE.equals(action.get("delete"))) {
            if ("trash".equals(msg.getFolder())) {
                msg.setDelStatus(1);
                msg.setDeleteAt(now);
            } else {
                msg.setFolder("trash");
            }
        } else if (Boolean.TRUE.equals(action.get("archive"))) {
            msg.setFolder("archive");
        } else if (action.get("move") != null && !String.valueOf(action.get("move")).isBlank()) {
            msg.setFolder(String.valueOf(action.get("move")));
        }
        if (action.get("label") != null && !String.valueOf(action.get("label")).isBlank()) {
            String labelId = String.valueOf(action.get("label"));
            List<String> labelIds = MailMessageService.parseIdArray(msg.getLabelIds());
            if (!labelIds.contains(labelId)) {
                labelIds.add(labelId);
                msg.setLabelIds(MailMessageService.writeIdArray(labelIds));
            }
        }
        msg.setUpdateAt(now);
        msg.setUpdater(userId);
        msg.setUpdaterName(userName);
    }

    /** 写手动应用历史（trigger_type='manual'） */
    private void writeApplyLog(String filterId, int appliedCount, String userId, String userName, Date now) {
        MailFilterApplyLog logEntry = new MailFilterApplyLog();
        logEntry.setFilterId(filterId);
        logEntry.setUserId(userId);
        logEntry.setAppliedCount(appliedCount);
        logEntry.setAppliedBy(userName);
        logEntry.setTriggerType("manual");
        logEntry.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        logEntry.setDelStatus(0);
        logEntry.setCreateAt(now);
        logEntry.setUpdateAt(now);
        logEntry.setCreator(userId);
        logEntry.setCreatorName(userName);
        logEntry.setUpdater(userId);
        logEntry.setUpdaterName(userName);
        dynamicDao.save(logEntry);
    }

    /** 当前用户收件箱既有邮件（folder='inbox' 非草稿；public 供单测 stub） */
    public List<MailMessage> listInboxMessages() {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("folder", "inbox"),
                        Filter.eq("isDraft", 0),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        List<MailMessage> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            if (id == null) {
                continue;
            }
            MailMessage msg = dynamicDao.queryForObject(MailMessage.class, String.valueOf(id));
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    // ==================== 校验 ====================

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("过滤器名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LEN) {
            throw new IllegalArgumentException(
                    "过滤器名称超长（上限 " + MAX_NAME_LEN + " 字符，实际 " + trimmed.length() + "）");
        }
        return trimmed;
    }

    /** 条件结构校验（field/operator 白名单 + value 必填；size 条件 value 必须为数字） */
    private void validateConditions(List<Map<String, Object>> conditions) {
        if (conditions == null) {
            return;
        }
        for (Map<String, Object> condition : conditions) {
            Object field = condition == null ? null : condition.get("field");
            Object operator = condition == null ? null : condition.get("operator");
            Object value = condition == null ? null : condition.get("value");
            if (field == null || !FIELDS.contains(String.valueOf(field))) {
                throw new IllegalArgumentException("条件字段非法: " + field + "（允许 " + FIELDS + "）");
            }
            if (operator == null || !OPERATORS.contains(String.valueOf(operator))) {
                throw new IllegalArgumentException("条件运算符非法: " + operator + "（允许 " + OPERATORS + "）");
            }
            if (value == null) {
                throw new IllegalArgumentException("条件值不能为空");
            }
            String v = String.valueOf(value);
            if (v.length() > MAX_CONDITION_VALUE_LEN) {
                throw new IllegalArgumentException(
                        "条件值超长（上限 " + MAX_CONDITION_VALUE_LEN + " 字符，实际 " + v.length() + "）");
            }
            if ("size".equals(String.valueOf(field))) {
                try {
                    Long.parseLong(v.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("size 条件的值必须为数字（字节）: " + v);
                }
            }
        }
    }

    /** 动作结构校验（键白名单 + folder 类动作互斥 + move 目标形态） */
    private void validateAction(Map<String, Object> action) {
        if (action == null) {
            return;
        }
        for (String key : action.keySet()) {
            if (!ACTION_KEYS.contains(key)) {
                throw new IllegalArgumentException("不支持的动作: " + key + "（允许 " + ACTION_KEYS + "）");
            }
        }
        int folderOps = 0;
        if (action.get("move") != null && !String.valueOf(action.get("move")).isBlank()) {
            folderOps++;
            String target = String.valueOf(action.get("move"));
            if (!PHYSICAL_FOLDERS.contains(target)
                    && !(target.startsWith("custom_") && target.length() > "custom_".length())) {
                throw new IllegalArgumentException(
                        "move 目标文件夹非法: " + target + "（允许 " + PHYSICAL_FOLDERS + " 或 custom_*）");
            }
        }
        if (Boolean.TRUE.equals(action.get("archive"))) {
            folderOps++;
        }
        if (Boolean.TRUE.equals(action.get("delete"))) {
            folderOps++;
        }
        if (folderOps > 1) {
            throw new IllegalArgumentException("move/archive/delete 动作互斥，只能设置其一");
        }
    }

    /** 执行前预校验（label 归属；move 目标在保存时已校验，此处防御 label 悬空引用） */
    private void validateActionExecutable(MailFilter filter, Map<String, Object> action) {
        Object label = action.get("label");
        if (label == null || String.valueOf(label).isBlank()) {
            return;
        }
        String labelId = String.valueOf(label);
        Map<String, MailLabel> owned = labelService.mapByIds(List.of(labelId));
        if (!owned.containsKey(labelId)) {
            throw new IllegalArgumentException(
                    "过滤器 " + filter.getName() + " 引用的标签不存在或不属于当前用户: " + labelId);
        }
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailFilter 契约（id 为雪花 string，与 P0/P1/P2/P4 id 口径一致） */
    public Map<String, Object> toResponse(MailFilter filter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", filter.getId());
        map.put("name", filter.getName());
        map.put("enabled", filter.getEnabled() == 1);
        map.put("conditions", parseConditions(filter.getConditionsJson()));
        map.put("action", parseAction(filter.getActionJson()));
        map.put("sortOrder", filter.getSortOrder());
        map.put("applyToExisting", filter.getApplyToExisting() == 1);
        map.put("createdAt", filter.getCreateAt() == null ? "" : filter.getCreateAt().toInstant().toString());
        return map;
    }

    // ==================== 内部辅助 ====================

    private int nextSortOrder(String userId) {
        int max = 0;
        for (MailFilter filter : listEntities()) {
            max = Math.max(max, filter.getSortOrder());
        }
        return max + 1;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new IllegalStateException("过滤器条件 JSON 解析失败: " + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAction(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("过滤器动作 JSON 解析失败: " + json, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("过滤器 JSON 序列化失败", e);
        }
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailFilter toEntity(Map<String, Object> row) {
        MailFilter filter = new MailFilter();
        filter.setId(str(row.get("id")));
        filter.setUserId(str(row.get("userId")));
        filter.setName(str(row.get("name")));
        Object enabled = row.get("enabled");
        filter.setEnabled(enabled instanceof Number n ? n.intValue() : 0);
        filter.setConditionsJson(str(row.get("conditionsJson")));
        filter.setActionJson(str(row.get("actionJson")));
        Object sortOrder = row.get("sortOrder");
        filter.setSortOrder(sortOrder instanceof Number n ? n.intValue() : 0);
        Object applyToExisting = row.get("applyToExisting");
        filter.setApplyToExisting(applyToExisting instanceof Number n ? n.intValue() : 0);
        filter.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        filter.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        filter.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        filter.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        filter.setCreator(str(row.get("creator")));
        filter.setCreatorName(str(row.get("creatorName")));
        filter.setUpdater(str(row.get("updater")));
        filter.setUpdaterName(str(row.get("updaterName")));
        return filter;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
