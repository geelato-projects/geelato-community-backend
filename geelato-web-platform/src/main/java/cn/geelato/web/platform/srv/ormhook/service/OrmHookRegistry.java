package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHook;
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
import java.util.List;
import java.util.Map;

/**
 * 钩子注册表：内存快照 + 显式刷新。
 * <p>
 * <b>模型（v3 定案）</b>：一条 Hook = 名称+地址+启停，无实体绑定、无事件选择——
 * 任何实体的任何事件都触发（钩子机制自身两张表除外，见
 * {@link #FORBIDDEN_TARGET_ENTITIES}，在监听器层硬排除防递归）。
 * 快照即启用 Hook 的不可变列表（volatile 整体替换）；读路径（supports/afterCommit）
 * 无锁、无 DB 查询；配置变更经 {@code OrmHookService} 写后 {@link #refresh()} 生效。
 * <p>
 * <b>启动语义</b>（ApplicationReadyEvent）：
 * <ul>
 *   <li>表未建（宿主未跑 scaffold init）→ warn + 空快照，特性静止（附加特性不得阻断宿主启动）</li>
 *   <li>表已建但存在坏规则（地址为空/非法）→ 抛异常中止启动，指明规则 id（不静默跳过）</li>
 * </ul>
 * 运行期 refresh 遇坏规则同样抛出，由调用方决定保留旧快照。
 * 直连 SQL 绕过服务校验写入的坏规则不受此保护面覆盖，属越界操作。
 *
 * @author geelato
 */
@Component
@Slf4j
public class OrmHookRegistry {

    /** 禁止触发的实体（防自触发/自 consumed）：本机制自身的两张表。 */
    public static final List<String> FORBIDDEN_TARGET_ENTITIES = List.of("platform_orm_hook", "platform_orm_hook_log");

    private static final String TABLE_NAME = "platform_orm_hook";

    private final Dao dao;

    /** 启用中的 Hook 列表；不可变快照，整体替换。 */
    private volatile List<OrmHook> snapshot = List.of();

    @Autowired
    public OrmHookRegistry(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!tableExists()) {
            log.warn("platform_orm_hook 表不存在（宿主未执行 scaffold init），ORM 钩子特性静止：监听器空转、不加载任何规则");
            return;
        }
        refresh();
        log.info("ORM 钩子已加载：{} 条（任何实体的任何事件均触发）", snapshot.size());
    }

    /** 全量重载并校验；坏规则抛 {@link IllegalStateException}（指明规则 id），原子保留旧快照。 */
    public synchronized void refresh() {
        List<OrmHook> hooks = queryEnabledHooks();
        List<String> problems = new ArrayList<>();
        for (OrmHook hook : hooks) {
            String problem = validate(hook);
            if (problem != null) {
                problems.add(String.format("规则[%s](%s)：%s", hook.getId(), hook.getTitle(), problem));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ORM 钩子存在配置错误：\n" + String.join("\n", problems));
        }
        this.snapshot = List.copyOf(hooks);
    }

    /** 是否存在启用中的 Hook（enabled() 粗开关用，应廉价）。 */
    public boolean hasHooks() {
        return !snapshot.isEmpty();
    }

    /** 启用中的 Hook 列表（不可变快照，永不 null）。 */
    public List<OrmHook> getHooks() {
        return snapshot;
    }

    /** 结构校验：通过返回 null，否则返回问题描述。 */
    private String validate(OrmHook hook) {
        if (hook.getHttpUrl() == null || hook.getHttpUrl().isBlank()) {
            return "地址（http_url）为空";
        }
        return null;
    }

    /** 查启用的 Hook：del_status=0 AND enable_status=1（直 SQL，列别名驼峰供 fastjson 映射）。 */
    private List<OrmHook> queryEnabledHooks() {
        String sql = "SELECT id, title, http_url AS httpUrl, enable_status AS enableStatus, tenant_code AS tenantCode "
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
