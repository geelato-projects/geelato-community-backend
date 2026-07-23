package cn.geelato.ide.entitydef.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.ide.entitydef.dto.IdeEntityDefinition;
import cn.geelato.ide.entitydef.dto.IdeEntityFieldDefinition;
import cn.geelato.ide.entitydef.dto.IdeEntityValidateResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IDE 实体定义业务服务。
 * <p>
 * 复用 platform_dev_table / platform_dev_column 两张表（数据兼容，MetaManager 自动识别），
 * 但 API 层完全自研（不走老的 DevTableController）。
 *
 * 核心流程：
 * <ol>
 *   <li>validate：校验 entityName 唯一性 + 字段合法性</li>
 *   <li>define：写 platform_dev_table + platform_dev_column，调 refreshDBMeta 让 /api/meta/* 立即识别</li>
 * </ol>
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeEntityService {

    /** entityName 命名规则：小写字母开头，只含小写字母/数字/下划线；不能 platform_ 前缀 */
    private static final String ENTITY_NAME_REGEX = "^(?!platform_)[a-z][a-z0-9_]*$";
    /** fieldName 命名规则：小写字母开头的驼峰 */
    private static final String FIELD_NAME_REGEX = "^[a-z][a-zA-Z0-9]*$";
    /** columnName 命名规则：小写字母开头的 snake_case */
    private static final String COLUMN_NAME_REGEX = "^[a-z][a-z0-9_]*$";
    /** 支持的数据类型（MVP） */
    private static final Set<String> SUPPORTED_DATA_TYPES = Set.of(
            "VARCHAR", "TEXT", "LONGTEXT", "INT", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "BIT", "DATETIME", "DATE", "TIME", "TIMESTAMP", "JSON");

    @Autowired
    private BaseService baseService;

    /**
     * 校验实体定义（不落库）。
     */
    public IdeEntityValidateResult validate(IdeEntityDefinition def) {
        IdeEntityValidateResult result = IdeEntityValidateResult.ok();
        if (def == null) {
            return IdeEntityValidateResult.fail("实体定义不能为空");
        }
        // entityName
        if (Strings.isBlank(def.getEntityName())) {
            result.addError("entityName 不能为空");
        } else if (!def.getEntityName().matches(ENTITY_NAME_REGEX)) {
            result.addError("entityName 必须小写字母开头、仅含小写字母/数字/下划线，且不能以 platform_ 开头: " + def.getEntityName());
        }
        // tableName 默认 = entityName
        if (Strings.isBlank(def.getTableName())) {
            def.setTableName(def.getEntityName());
        }
        // title
        if (Strings.isBlank(def.getTitle())) {
            result.addError("title 不能为空");
        }
        // connectId
        if (Strings.isBlank(def.getConnectId())) {
            result.addError("connectId 不能为空（建实体必需）");
        }
        // columns
        if (def.getColumns() == null || def.getColumns().isEmpty()) {
            result.addError("columns 至少 1 个字段");
        } else {
            boolean hasPk = false;
            Set<String> fieldNames = new HashSet<>();
            Set<String> columnNames = new HashSet<>();
            for (int i = 0; i < def.getColumns().size(); i++) {
                IdeEntityFieldDefinition f = def.getColumns().get(i);
                String prefix = "columns[" + i + "] ";
                if (Strings.isBlank(f.getFieldName()) || !f.getFieldName().matches(FIELD_NAME_REGEX)) {
                    result.addError(prefix + "fieldName 必须小写字母开头的驼峰: " + f.getFieldName());
                } else if (!fieldNames.add(f.getFieldName())) {
                    result.addError(prefix + "fieldName 重复: " + f.getFieldName());
                }
                if (Strings.isBlank(f.getColumnName()) || !f.getColumnName().matches(COLUMN_NAME_REGEX)) {
                    result.addError(prefix + "columnName 必须小写字母开头的 snake_case: " + f.getColumnName());
                } else if (!columnNames.add(f.getColumnName())) {
                    result.addError(prefix + "columnName 重复: " + f.getColumnName());
                }
                if (Strings.isBlank(f.getDataType()) || !SUPPORTED_DATA_TYPES.contains(f.getDataType().toUpperCase())) {
                    result.addError(prefix + "dataType 不支持: " + f.getDataType() + "（支持: " + SUPPORTED_DATA_TYPES + "）");
                } else {
                    f.setDataType(f.getDataType().toUpperCase());
                }
                if (Boolean.TRUE.equals(f.getPrimaryKey())) {
                    hasPk = true;
                    if (Boolean.FALSE.equals(f.getNullable()) && Boolean.TRUE.equals(f.getAutoIncrement())
                            && !(f.getDataType().equals("BIGINT") || f.getDataType().equals("INT"))) {
                        result.addWarning(prefix + "自增主键建议用 BIGINT/INT，当前: " + f.getDataType());
                    }
                }
            }
            if (!hasPk) {
                result.addError("至少需要 1 个 primaryKey=true 的字段");
            }
        }
        // 唯一性（entityName + connectId）
        if (result.isValid() && existsByEntityName(def.getEntityName(), def.getConnectId())) {
            result.addWarning("实体已存在（将执行更新）: " + def.getEntityName());
        }
        return result;
    }

    /**
     * 创建/更新实体（写 platform_dev_table + platform_dev_column + refreshDBMeta）。
     */
    public IdeEntityDefinition define(IdeEntityDefinition def) {
        IdeEntityValidateResult vr = validate(def);
        if (!vr.isValid()) {
            throw new IllegalArgumentException("校验失败: " + String.join("; ", vr.getErrors()));
        }
        Dao dao = baseService.dao;
        User user = SecurityContext.getCurrentUser();
        String userId = user != null ? user.getUserId() : "system";
        String userName = user != null ? user.getUserName() : "system";
        String tenantCode = SessionCtx.getCurrentTenantCode();
        Date now = new Date();

        // 1. platform_dev_table（有则 UPDATE，无则 INSERT）
        String tableId = ensureDevTable(def, dao, userId, userName, tenantCode, now);

        // 2. 逻辑删旧字段
        dao.getJdbcTemplate().update(
                "UPDATE platform_dev_column SET del_status = 1, delete_at = ?, update_at = ?, updater = ? WHERE table_id = ? AND del_status = 0",
                now, now, userId, tableId);

        // 3. 批量 INSERT 新字段
        int ordinal = 0;
        for (IdeEntityFieldDefinition f : def.getColumns()) {
            ordinal++;
            insertDevColumn(tableId, def, f, ordinal, dao, userId, userName, tenantCode, now);
        }

        // 4. 刷新 MetaManager 缓存（让 /api/meta/* 立即识别）
        try {
            MetaManager.singleInstance().refreshDBMeta(def.getEntityName());
            log.info("MetaManager.refreshDBMeta 成功: {}", def.getEntityName());
        } catch (Exception e) {
            log.error("MetaManager.refreshDBMeta 失败: {} - {}", def.getEntityName(), e.getMessage());
            // 不阻断主流程，元数据已写入，重启后会被 parseDBMeta 扫到
        }

        return def;
    }

    /**
     * 按 entityName 取实体完整定义。
     */
    public IdeEntityDefinition getByEntityName(String entityName) {
        if (Strings.isBlank(entityName)) {
            return null;
        }
        Dao dao = baseService.dao;
        dao.setDefaultFilter(true, baseService.filterGroup);
        List<Map<String, Object>> tables = dao.nativeQueryForMapList(
                "SELECT * FROM platform_dev_table WHERE entity_name = ? AND del_status = 0", new Object[]{entityName});
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        Map<String, Object> table = tables.get(0);
        String tableId = String.valueOf(table.get("id"));
        IdeEntityDefinition def = new IdeEntityDefinition();
        def.setEntityName((String) table.get("entity_name"));
        def.setTableName((String) table.get("table_name"));
        def.setTitle((String) table.get("title"));
        def.setConnectId((String) table.get("connect_id"));
        def.setDbType((String) table.get("db_type"));
        def.setTableSchema((String) table.get("table_schema"));
        def.setDescription((String) table.get("description"));
        def.setAppId((String) table.get("app_id"));
        // 字段
        List<Map<String, Object>> cols = dao.nativeQueryForMapList(
                "SELECT * FROM platform_dev_column WHERE table_id = ? AND del_status = 0 ORDER BY ordinal_position",
                new Object[]{tableId});
        List<IdeEntityFieldDefinition> columns = new ArrayList<>();
        if (cols != null) {
            for (Map<String, Object> c : cols) {
                IdeEntityFieldDefinition f = mapToField(c);
                columns.add(f);
            }
        }
        def.setColumns(columns);
        return def;
    }

    /**
     * 列出实体（按 connectId 过滤，可选 keyword 模糊匹配）。
     */
    public List<Map<String, Object>> list(String connectId, String keyword) {
        Dao dao = baseService.dao;
        StringBuilder sql = new StringBuilder(
                "SELECT id, entity_name, table_name, title, connect_id, db_type, description, source_type, synced, enable_status, create_at, update_at FROM platform_dev_table WHERE del_status = 0");
        List<Object> params = new ArrayList<>();
        if (Strings.isNotBlank(connectId)) {
            sql.append(" AND connect_id = ?");
            params.add(connectId);
        }
        if (Strings.isNotBlank(keyword)) {
            sql.append(" AND (entity_name LIKE ? OR title LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY update_at DESC");
        return dao.nativeQueryForMapList(sql.toString(), params.toArray());
    }

    /**
     * 逻辑删除（platform_dev_table + 关联字段）。
     */
    public void delete(String entityName) {
        Dao dao = baseService.dao;
        User user = SecurityContext.getCurrentUser();
        String userId = user != null ? user.getUserId() : "system";
        Date now = new Date();
        // 查 id
        List<Map<String, Object>> tables = dao.nativeQueryForMapList(
                "SELECT id FROM platform_dev_table WHERE entity_name = ? AND del_status = 0", new Object[]{entityName});
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("实体不存在: " + entityName);
        }
        String tableId = String.valueOf(tables.get(0).get("id"));
        dao.getJdbcTemplate().update(
                "UPDATE platform_dev_table SET del_status = 1, delete_at = ?, update_at = ?, updater = ? WHERE id = ?",
                now, now, userId, tableId);
        dao.getJdbcTemplate().update(
                "UPDATE platform_dev_column SET del_status = 1, delete_at = ?, update_at = ?, updater = ? WHERE table_id = ?",
                now, now, userId, tableId);
    }

    /**
     * 列出已用 connectId（用于插件 connectId 选择器）。
     */
    public List<String> listConnectIds() {
        Dao dao = baseService.dao;
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT DISTINCT connect_id FROM platform_dev_table WHERE del_status = 0 AND connect_id IS NOT NULL", new Object[]{});
        List<String> result = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Object v = r.get("connect_id");
                if (v != null) {
                    result.add(String.valueOf(v));
                }
            }
        }
        return result;
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    private boolean existsByEntityName(String entityName, String connectId) {
        Dao dao = baseService.dao;
        Long count = dao.nativeQueryForObject(
                "SELECT COUNT(*) FROM platform_dev_table WHERE entity_name = ? AND del_status = 0",
                new Object[]{entityName}, Long.class);
        return count != null && count > 0;
    }

    /**
     * 写 platform_dev_table（有则 UPDATE，无则 INSERT），返回 id。
     */
    private String ensureDevTable(IdeEntityDefinition def, Dao dao, String userId, String userName, String tenantCode, Date now) {
        List<Map<String, Object>> existing = dao.nativeQueryForMapList(
                "SELECT id FROM platform_dev_table WHERE entity_name = ? AND del_status = 0",
                new Object[]{def.getEntityName()});
        String id;
        if (existing != null && !existing.isEmpty()) {
            id = String.valueOf(existing.get(0).get("id"));
            dao.getJdbcTemplate().update(
                    "UPDATE platform_dev_table SET table_name = ?, title = ?, connect_id = ?, db_type = ?, table_schema = ?, description = ?, app_id = ?, update_at = ?, updater = ?, updater_name = ?, enable_status = 1, synced = 0 WHERE id = ?",
                    def.getTableName(), def.getTitle(), def.getConnectId(), def.getDbType(), def.getTableSchema(),
                    def.getDescription(), def.getAppId(), now, userId, userName, id);
        } else {
            id = generateId();
            dao.getJdbcTemplate().update(
                    "INSERT INTO platform_dev_table (id, entity_name, table_name, title, connect_id, db_type, table_schema, table_type, table_comment, description, app_id, source_type, enable_status, synced, del_status, seq_no, tenant_code, creator, creator_name, create_at, update_at, updater, updater_name, cache_type) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'entity', ?, ?, ?, 'creation', 1, 0, 0, 0, ?, ?, ?, ?, ?, ?, ?, 'none')",
                    id, def.getEntityName(), def.getTableName(), def.getTitle(), def.getConnectId(), def.getDbType(),
                    def.getTableSchema(), def.getTitle(), def.getDescription(), def.getAppId(),
                    tenantCode, userId, userName, now, now, userId, userName);
        }
        return id;
    }

    /**
     * 写 platform_dev_column 一行。
     */
    private void insertDevColumn(String tableId, IdeEntityDefinition def, IdeEntityFieldDefinition f, int ordinal,
                                  Dao dao, String userId, String userName, String tenantCode, Date now) {
        String id = generateId();
        String columnType = buildColumnType(f);
        boolean isNullable = !Boolean.FALSE.equals(f.getNullable());
        boolean isPk = Boolean.TRUE.equals(f.getPrimaryKey());
        boolean isUnique = Boolean.TRUE.equals(f.getUnique());
        boolean isAutoInc = Boolean.TRUE.equals(f.getAutoIncrement());
        boolean isEncrypted = Boolean.TRUE.equals(f.getEncrypted());
        dao.getJdbcTemplate().update(
                "INSERT INTO platform_dev_column (id, app_id, table_id, table_schema, table_name, table_catalog, " +
                        "title, field_name, column_name, column_comment, description, ordinal_position, " +
                        "data_type, column_type, character_maxinum_length, numeric_precision, numeric_scale, numeric_signed, " +
                        "column_key, is_nullable, is_unique, auto_increment, extra, column_default, encrypted, " +
                        "synced, linked, drawed, enable_status, del_status, seq_no, tenant_code, " +
                        "creator, creator_name, create_at, update_at, updater, updater_name) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, b'0', ?, ?, ?, ?, ?, ?, ?, " +
                        "0, 0, 0, 1, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, def.getAppId(), tableId, def.getTableSchema(), def.getTableName(), null,
                f.getTitle(), f.getFieldName(), f.getColumnName(), f.getComment(), null, ordinal,
                f.getDataType(), columnType,
                "VARCHAR".equals(f.getDataType()) || "TEXT".equals(f.getDataType()) ? f.getCharMaxLength() : null,
                "DECIMAL".equals(f.getDataType()) || "INT".equals(f.getDataType()) || "BIGINT".equals(f.getDataType()) ? f.getNumericPrecision() : null,
                "DECIMAL".equals(f.getDataType()) ? f.getNumericScale() : null,
                isPk ? 1 : 0, isNullable ? 1 : 0, isUnique ? 1 : 0, isAutoInc ? 1 : 0,
                isAutoInc ? "AUTO_INCREMENT" : null, f.getDefaultValue(), isEncrypted ? 1 : 0,
                ordinal, tenantCode, userId, userName, now, now, userId, userName);
    }

    /**
     * 构造 MySQL 列类型字符串，如 VARCHAR(64) / DECIMAL(20,2) / BIGINT / DATETIME。
     */
    private String buildColumnType(IdeEntityFieldDefinition f) {
        String t = f.getDataType();
        if (t == null) {
            return "VARCHAR(64)";
        }
        switch (t) {
            case "VARCHAR":
                return "VARCHAR(" + (f.getCharMaxLength() != null ? f.getCharMaxLength() : 64) + ")";
            case "DECIMAL":
                return "DECIMAL(" + (f.getNumericPrecision() != null ? f.getNumericPrecision() : 20) + "," + (f.getNumericScale() != null ? f.getNumericScale() : 0) + ")";
            case "INT": case "BIGINT": case "SMALLINT": case "TINYINT":
            case "TEXT": case "LONGTEXT": case "BIT": case "JSON":
                return t;
            case "DATETIME": case "DATE": case "TIME": case "TIMESTAMP":
                return t;
            default:
                return t;
        }
    }

    @SuppressWarnings("unchecked")
    private IdeEntityFieldDefinition mapToField(Map<String, Object> c) {
        IdeEntityFieldDefinition f = new IdeEntityFieldDefinition();
        f.setFieldName((String) c.get("field_name"));
        f.setColumnName((String) c.get("column_name"));
        f.setTitle((String) c.get("title"));
        f.setDataType((String) c.get("data_type"));
        Object charLen = c.get("character_maxinum_length");
        if (charLen instanceof Number) {
            f.setCharMaxLength(((Number) charLen).longValue());
        }
        Object prec = c.get("numeric_precision");
        if (prec instanceof Number) {
            f.setNumericPrecision(((Number) prec).intValue());
        }
        Object scale = c.get("numeric_scale");
        if (scale instanceof Number) {
            f.setNumericScale(((Number) scale).intValue());
        }
        Object isNullable = c.get("is_nullable");
        f.setNullable(!(isNullable instanceof Number) || ((Number) isNullable).intValue() != 0);
        Object columnKey = c.get("column_key");
        f.setPrimaryKey(columnKey instanceof Number && ((Number) columnKey).intValue() != 0);
        Object isUnique = c.get("is_unique");
        f.setUnique(isUnique instanceof Number && ((Number) isUnique).intValue() != 0);
        Object autoInc = c.get("auto_increment");
        f.setAutoIncrement(autoInc instanceof Number && ((Number) autoInc).intValue() != 0);
        f.setDefaultValue((String) c.get("column_default"));
        Object encrypted = c.get("encrypted");
        f.setEncrypted(encrypted instanceof Number && ((Number) encrypted).intValue() != 0);
        f.setComment((String) c.get("column_comment"));
        return f;
    }

    private String generateId() {
        return String.valueOf(System.currentTimeMillis() * 1000 + (UUID.randomUUID().hashCode() & 0x3FF));
    }
}
