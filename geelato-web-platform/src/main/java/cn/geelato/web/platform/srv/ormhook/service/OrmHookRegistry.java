package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHook;
import cn.geelato.web.platform.srv.ormhook.enums.OrmHookEventEnum;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionExecutor;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionManager;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钩子规则注册表：内存快照 + 显式刷新。
 * <p>
 * <b>快照语义</b>：volatile 不可变 Map 整体替换（entityName → eventType → 规则列表），
 * 读路径（监听器 supports/afterCommit）无锁、无 DB 查询；配置变更经
 * {@code OrmHookService} 写后 {@link #refresh()} 生效。
 * <p>
 * <b>启动语义</b>（ApplicationReadyEvent）：
 * <ul>
 *   <li>表未建（宿主未跑 scaffold init）→ warn + 空快照，特性静止（附加特性不得阻断宿主启动）</li>
 *   <li>表已建但存在结构坏规则（事件非法/实体不存在/动作类型无实现/动作必填项缺失/钩子表自配）
 *       → 抛异常中止启动，指明规则 id（配置坏了就该被发现，不静默跳过）</li>
 * </ul>
 * 运行期 refresh 遇坏规则同样抛出，由调用方决定回滚配置或保留旧快照。
 * 直连 SQL 绕过服务校验写入的坏规则不受此保护面覆盖，属越界操作。
 *
 * @author geelato
 */
@Component
@Slf4j
public class OrmHookRegistry {

    /** 禁止配钩子的实体（防自触发/自 consumed）：本机制自身的两张表。 */
    public static final List<String> FORBIDDEN_TARGET_ENTITIES = List.of("platform_orm_hook", "platform_orm_hook_log");

    private static final String TABLE_NAME = "platform_orm_hook";

    private final Dao dao;
    private final OrmHookActionManager actionManager;

    /** entityName → event.value → 规则列表；不可变快照，整体替换。 */
    private volatile Map<String, Map<String, List<OrmHook>>> snapshot = Map.of();

    @Autowired
    public OrmHookRegistry(@Qualifier("primaryDao") Dao dao, OrmHookActionManager actionManager) {
        this.dao = dao;
        this.actionManager = actionManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!tableExists()) {
            log.warn("platform_orm_hook 表不存在（宿主未执行 scaffold init），ORM 钩子特性静止：监听器空转、不加载任何规则");
            return;
        }
        refresh();
        log.info("ORM 钩子规则已加载：{} 个实体配钩", snapshot.size());
    }

    /** 全量重载并校验；结构坏规则抛 {@link IllegalStateException}（指明规则 id），原子保留旧快照。 */
    public synchronized void refresh() {
        List<OrmHook> rules = queryEnabledRules();
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, List<OrmHook>>> next = new LinkedHashMap<>();
        for (OrmHook rule : rules) {
            String problem = validate(rule);
            if (problem != null) {
                problems.add(String.format("规则[%s](%s)：", rule.getId(), rule.getTitle()) + problem);
                continue;
            }
            next.computeIfAbsent(rule.getEntityName(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(rule.getEventType(), k -> new ArrayList<>())
                    .add(rule);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ORM 钩子规则存在配置错误：\n" + String.join("\n", problems));
        }
        this.snapshot = next;
    }

    /** 快照是否非空（enabled() 粗开关用，应廉价）。 */
    public boolean hasRules() {
        return !snapshot.isEmpty();
    }

    /** 取实体在某事件的所有规则（快照查询，无匹配返回空列表，永不 null）。 */
    public List<OrmHook> getRules(String entityName, OrmHookEventEnum event) {
        if (entityName == null || event == null) {
            return List.of();
        }
        Map<String, List<OrmHook>> byEvent = snapshot.get(entityName);
        if (byEvent == null) {
            return List.of();
        }
        List<OrmHook> rules = byEvent.get(event.value());
        return rules == null ? List.of() : rules;
    }

    /** 实体在某事件是否有规则（supports() 细匹配用）。 */
    public boolean hasRuleFor(String entityName, OrmHookEventEnum event) {
        return !getRules(entityName, event).isEmpty();
    }

    /** 结构校验：通过返回 null，否则返回问题描述。 */
    private String validate(OrmHook rule) {
        if (OrmHookEventEnum.of(rule.getEventType()) == null) {
            return "非法事件类型 " + rule.getEventType() + "（合法值：insert | update | delete）";
        }
        String entityName = rule.getEntityName();
        if (entityName == null || entityName.isBlank()) {
            return "实体名称为空";
        }
        if (FORBIDDEN_TARGET_ENTITIES.contains(entityName)) {
            return "禁止对钩子机制自身的表配置钩子：" + entityName;
        }
        try {
            if (MetaManager.singleInstance().getByEntityName(entityName) == null) {
                return "实体 " + entityName + " 不存在于元数据";
            }
        } catch (Exception e) {
            return "解析实体 " + entityName + " 元数据失败：" + e.getMessage();
        }
        if (!actionManager.supports(rule.getActionType())) {
            return "动作类型 " + rule.getActionType() + " 无可用执行器（当前支持：script | http）";
        }
        if (OrmHookActionExecutor.TYPE_SCRIPT.equals(rule.getActionType())
                && (rule.getScriptContent() == null || rule.getScriptContent().isBlank())) {
            return "script 动作缺少脚本内容（script_content）";
        }
        if (OrmHookActionExecutor.TYPE_HTTP.equals(rule.getActionType())) {
            if (rule.getHttpUrl() == null || rule.getHttpUrl().isBlank()) {
                return "http 动作缺少接口地址（http_url）";
            }
        }
        return null;
    }

    /** 查启用的规则：del_status=0 AND enable_status=1（直 SQL，列别名驼峰供 fastjson 映射）。 */
    private List<OrmHook> queryEnabledRules() {
        String sql = "SELECT id, title, entity_name AS entityName, event_type AS eventType, "
                + "action_type AS actionType, script_content AS scriptContent, "
                + "http_method AS httpMethod, http_url AS httpUrl, http_headers AS httpHeaders, "
                + "http_body AS httpBody, enable_status AS enableStatus, tenant_code AS tenantCode "
                + "FROM " + TABLE_NAME + " WHERE del_status = 0 AND enable_status = 1";
        List<Map<String, Object>> rows = dao.getJdbcTemplate().queryForList(sql);
        List<OrmHook> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(JSON.parseObject(JSON.toJSONString(row), OrmHook.class));
        }
        return list;
    }

    /** 表存在性检查（大小写三态兼容，对齐 AppScaffoldSchemaInitializer）。 */
    private boolean tableExists() {
        try {
            return Boolean.TRUE.equals(dao.getJdbcTemplate().execute((ConnectionCallback<Boolean>) con -> {
                DatabaseMetaData md = con.getMetaData();
                return tableExists(md, con.getCatalog(), con.getSchema(), TABLE_NAME)
                        || tableExists(md, con.getCatalog(), con.getSchema(), TABLE_NAME.toUpperCase())
                        || tableExists(md, con.getCatalog(), con.getSchema(), TABLE_NAME.toLowerCase());
            }));
        } catch (DataAccessException e) {
            log.warn("检查 {} 表存在性失败：{}", TABLE_NAME, e.getMessage());
            return false;
        }
    }

    private boolean tableExists(DatabaseMetaData md, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet tables = md.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
