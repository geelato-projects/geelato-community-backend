package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.mail.entity.MailSetting;
import cn.geelato.mail.util.MailSessionCtx;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 邮件通用设置服务（用户级 KV，key='general'，upsert 语义）。
 *
 * <p>契约：GET /api/mail/settings/general 返回完整 MailGeneralSettings（10 字段，
 * 未保存过返回默认值快照）；PATCH 为 Partial 合并更新（前端乐观更新后全量快照提交，
 * 兼容真正的部分字段提交），落库前做键白名单 + 类型/枚举/取值范围校验（fail-fast 40000）。
 *
 * <p>通知开关并入 general.enableNotifications（前端契约字段），不独立建 key/端点。
 *
 * <p>trashCleanupDays 语义：number=天数；JSON 显式 null=永不自动清理。
 * 反序列化为 Map 时可区分「键缺失」（不动）与「显式 null」（清除为永不清理）。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailSettingService {

    /** 通用设置 key */
    public static final String KEY_GENERAL = "general";

    private static final Set<String> DENSITY_VALUES = Set.of("compact", "comfortable", "default");
    private static final Set<String> VIEW_MODE_VALUES = Set.of("list", "conversation");
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "showMailSize", "showPreview", "enableNotifications",
            "enableKeyboardShortcuts", "enableConversationMode", "autoMarkRead");
    /** itemsPerPage 允许范围（列表分页大小） */
    private static final int MIN_ITEMS_PER_PAGE = 5;
    private static final int MAX_ITEMS_PER_PAGE = 200;
    /** trashCleanupDays 允许范围（null=永不清理） */
    private static final int MIN_TRASH_DAYS = 1;
    private static final int MAX_TRASH_DAYS = 3650;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    // ==================== 查询 ====================

    /** 当前用户通用设置（未保存过返回默认值快照；已保存按默认值兜底合并缺失字段） */
    public Map<String, Object> getGeneral() {
        Map<String, Object> result = defaults();
        MailSetting row = findByKey(MailSessionCtx.getCurrentUserId(), KEY_GENERAL);
        if (row != null && row.getSettingValue() != null && !row.getSettingValue().isBlank()) {
            result.putAll(parseJson(row.getSettingValue()));
        }
        return result;
    }

    /** 查询当前用户指定 key 的设置行（无返回 null）；public 供单测 stub */
    public MailSetting findByKey(String userId, String key) {
        MetaQuery query = MetaFactory.query(MailSetting.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("settingKey", key),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    // ==================== 写 ====================

    /**
     * Partial 合并更新通用设置（upsert：无行则建，有行则合并后出现字段）。
     *
     * @param patch 前端提交的设置片段（键缺失=不动；显式 null 仅 trashCleanupDays 合法）
     * @throws IllegalArgumentException 未知键/类型错误/枚举或范围非法（调用方转 40000）
     */
    public void patchGeneral(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new IllegalArgumentException("设置内容不能为空");
        }
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            validateField(entry.getKey(), entry.getValue());
        }
        String userId = MailSessionCtx.getCurrentUserId();
        MailSetting row = findByKey(userId, KEY_GENERAL);
        Map<String, Object> merged = row == null || row.getSettingValue() == null
                || row.getSettingValue().isBlank()
                ? new LinkedHashMap<>()
                : parseJson(row.getSettingValue());
        merged.putAll(patch);

        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        if (row == null) {
            row = new MailSetting();
            row.setUserId(userId);
            row.setSettingKey(KEY_GENERAL);
            row.setTenantCode(MailSessionCtx.getCurrentTenantCode());
            row.setDelStatus(0);
            row.setCreateAt(now);
            row.setCreator(userId);
            row.setCreatorName(userName);
        }
        row.setSettingValue(writeJson(merged));
        row.setUpdateAt(now);
        row.setUpdater(userId);
        row.setUpdaterName(userName);
        dynamicDao.save(row);
    }

    // ==================== 校验 ====================

    /** 单字段校验（白名单 + 类型 + 枚举/范围）；非法抛 IllegalArgumentException */
    private void validateField(String key, Object value) {
        switch (key) {
            case "density" -> requireEnum(key, value, DENSITY_VALUES);
            case "viewMode" -> requireEnum(key, value, VIEW_MODE_VALUES);
            case "itemsPerPage" -> requireIntRange(key, value, MIN_ITEMS_PER_PAGE, MAX_ITEMS_PER_PAGE);
            case "trashCleanupDays" -> {
                if (value != null) {
                    requireIntRange(key, value, MIN_TRASH_DAYS, MAX_TRASH_DAYS);
                }
            }
            default -> {
                if (BOOLEAN_KEYS.contains(key)) {
                    if (!(value instanceof Boolean)) {
                        throw new IllegalArgumentException("设置项 " + key + " 必须为布尔值");
                    }
                } else {
                    throw new IllegalArgumentException("不支持的设置项: " + key);
                }
            }
        }
    }

    private void requireEnum(String key, Object value, Set<String> allowed) {
        if (!(value instanceof String s) || !allowed.contains(s)) {
            throw new IllegalArgumentException(
                    "设置项 " + key + " 取值非法: " + value + "（允许 " + allowed + "）");
        }
    }

    private void requireIntRange(String key, Object value, int min, int max) {
        if (!(value instanceof Number n) || n.intValue() < min || n.intValue() > max) {
            throw new IllegalArgumentException(
                    "设置项 " + key + " 必须为 " + min + "-" + max + " 的整数" + (value == null ? "" : "，实际: " + value));
        }
    }

    // ==================== 内部辅助 ====================

    /** 默认值快照（与前端 store fetchGeneralSettings 失败兜底默认值同口径） */
    private Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("density", "default");
        map.put("showMailSize", false);
        map.put("viewMode", "list");
        map.put("showPreview", true);
        map.put("enableNotifications", true);
        map.put("enableKeyboardShortcuts", true);
        map.put("enableConversationMode", false);
        map.put("autoMarkRead", true);
        map.put("itemsPerPage", 20);
        map.put("trashCleanupDays", 30);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("设置 JSON 解析失败: " + json, e);
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("设置 JSON 序列化失败", e);
        }
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailSetting toEntity(Map<String, Object> row) {
        MailSetting setting = new MailSetting();
        setting.setId(str(row.get("id")));
        setting.setUserId(str(row.get("userId")));
        setting.setSettingKey(str(row.get("settingKey")));
        setting.setSettingValue(str(row.get("settingValue")));
        setting.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        setting.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
        setting.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        setting.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        setting.setCreator(str(row.get("creator")));
        setting.setCreatorName(str(row.get("creatorName")));
        setting.setUpdater(str(row.get("updater")));
        setting.setUpdaterName(str(row.get("updaterName")));
        return setting;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
