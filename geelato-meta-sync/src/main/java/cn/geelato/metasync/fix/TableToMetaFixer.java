package cn.geelato.metasync.fix;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.utils.SchemaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 物理表 → 实体定义（platform_dev_column）补偿。
 * <p>
 * 把物理表列同步到 platform_dev_column：缺失则插入，存在则按需更新。
 * 默认 dry-run 仅返回预览 SQL，需显式 apply=true 才写库。
 *
 * @author geemeta
 */
public class TableToMetaFixer {

    private static final Logger log = LoggerFactory.getLogger(TableToMetaFixer.class);

    /** 补偿写库的创建者/更新者标识（裸 SQL 无 ORM 拦截器，固定为工具身份） */
    private static final String OPERATOR = "meta-sync";

    private final Dao dao;

    public TableToMetaFixer(Dao dao) {
        this.dao = dao;
    }

    /**
     * 结果：包含预览 SQL 列表与执行情况。
     */
    public static class FixResult {
        /** 预览 SQL 语句（dry-run 与 apply 都会填充） */
        public List<String> previewSql = new ArrayList<>();
        /** 实际执行条数（仅 apply=true 时非 0） */
        public int executed;
        /** 是否已写库 */
        public boolean applied;

        public FixResult(boolean applied) {
            this.applied = applied;
        }
    }

    /**
     * 把指定物理表的列同步到 platform_dev_column。
     * <p>先确保 platform_dev_table 存在（缺失则用雪花 id 新建，app_id/tenant_code 取界面入参），
     * 再以该 tableId 回填每个 column 的 table_id。
     *
     * @param tableName 物理表名
     * @param entityName 对应的实体名（可为空则用 tableName）
     * @param appId 应用 id（界面指定，必填）
     * @param tenantCode 租户编码（界面指定，必填）
     * @param apply true=写库，false=仅返回预览 SQL
     */
    public FixResult syncTableToMeta(String tableName, String entityName, String appId, String tenantCode, boolean apply) {
        FixResult result = new FixResult(apply);
        if (StringUtils.isBlank(tableName)) {
            return result;
        }
        if (StringUtils.isBlank(entityName)) {
            entityName = tableName;
        }
        JdbcTemplate jdbc = dao.getJdbcTemplate();

        // 1. 查物理表列
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                tableName);
        if (rows == null || rows.isEmpty()) {
            log.warn("物理表 {} 不存在或无列", tableName);
            return result;
        }
        List<SchemaColumn> schemaColumns = SchemaUtils.buildData(SchemaColumn.class, rows);

        // 2. 确保 platform_dev_table 存在：缺失则先插入（雪花 id），再以其 id 回填 column.table_id
        String tableId;
        List<Map<String, Object>> t = jdbc.queryForList(
                "SELECT id FROM platform_dev_table WHERE entity_name = ? AND del_status = ? LIMIT 1",
                entityName, ColumnDefault.DEL_STATUS_VALUE);
        if (t != null && !t.isEmpty() && t.get(0).get("id") != null) {
            tableId = t.get(0).get("id").toString();
        } else {
            java.sql.Timestamp now = new java.sql.Timestamp(new Date().getTime());
            tableId = String.valueOf(UIDGenerator.generate());
            SqlStatement tableStmt = buildTableInsert(tableId, appId, tenantCode, entityName, tableName, now);
            result.previewSql.add(tableStmt.toPreview());
            if (apply) {
                jdbc.update(tableStmt.getSql(), tableStmt.getParams());
                result.executed++;
            }
        }

        // 3. 现有 platform_dev_column 按 column_name 索引
        Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(tableId)) {
            List<Map<String, Object>> existRows = jdbc.queryForList(
                    "SELECT * FROM platform_dev_column WHERE table_id = ? AND del_status = ?",
                    tableId, ColumnDefault.DEL_STATUS_VALUE);
            if (existRows != null) {
                for (Map<String, Object> r : existRows) {
                    Object cn = r.get("column_name");
                    if (cn != null) {
                        existing.put(cn.toString().toLowerCase(Locale.ENGLISH), r);
                    }
                }
            }
        }

        // 4. 逐列处理：缺失→insert，存在→按需 update
        for (SchemaColumn sc : schemaColumns) {
            ColumnMeta cm = sc.convertIntoMeta(null);
            String colLower = StringUtils.isNotBlank(sc.getColumnName()) ? sc.getColumnName().toLowerCase(Locale.ENGLISH) : null;
            if (colLower == null) {
                continue;
            }
            if (!existing.containsKey(colLower)) {
                // insert
                SqlStatement stmt = buildInsert(tableId, appId, tenantCode, tableName, sc, cm);
                result.previewSql.add(stmt.toPreview());
                if (apply) {
                    jdbc.update(stmt.getSql(), stmt.getParams());
                    result.executed++;
                }
            } else {
                // update（仅当归一化基础类型或 nullable 有真实差异时；格式差异忽略）
                Map<String, Object> exist = existing.get(colLower);
                if (needUpdate(exist, cm)) {
                    String existId = exist.get("id") == null ? null : exist.get("id").toString();
                    SqlStatement stmt = buildUpdate(exist, existId, cm);
                    result.previewSql.add(stmt.toPreview());
                    if (apply) {
                        jdbc.update(stmt.getSql(), stmt.getParams());
                        result.executed++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 是否需要更新：按归一化基础类型比较（int 族/varchar 族等同组视为一致），
     * 完整 column_type 串不作为依据（afterSet 重拼的 INT(10) 与物理库 int 是格式差异，非缺陷）。
     */
    private boolean needUpdate(Map<String, Object> exist, ColumnMeta cm) {
        String existDataType = exist.get("data_type") == null ? null : exist.get("data_type").toString();
        // 真实类型冲突（如 varchar vs int）
        if (existDataType != null && cm.getDataType() != null
                && !normalizeBaseType(existDataType).equals(normalizeBaseType(cm.getDataType()))) {
            return true;
        }
        // nullable 约束差异
        boolean existNullable = parseBoolean(exist.get("is_nullable"), true);
        if (existNullable != cm.isNullable()) {
            return true;
        }
        return false;
    }

    /**
     * 归一化基础类型：同族类型视为一致（与 ConsistencyChecker 的分组一致）。
     * 如 int/integer/tinyint → int；char/varchar/text → varchar；decimal/numeric/double/float → decimal。
     */
    private static String normalizeBaseType(String dataType) {
        if (dataType == null) {
            return null;
        }
        String t = dataType.toLowerCase(Locale.ENGLISH).trim();
        if (t.equals("int") || t.equals("integer") || t.equals("tinyint") || t.equals("smallint") || t.equals("mediumint")) {
            return "int";
        }
        if (t.equals("char") || t.equals("varchar") || t.equals("text") || t.equals("tinytext") || t.equals("mediumtext") || t.equals("longtext") || t.equals("json")) {
            return "varchar";
        }
        if (t.equals("decimal") || t.equals("numeric") || t.equals("double") || t.equals("float")) {
            return "decimal";
        }
        if (t.equals("blob") || t.equals("tinyblob") || t.equals("mediumblob") || t.equals("longblob")) {
            return "blob";
        }
        if (t.equals("datetime") || t.equals("timestamp")) {
            return "datetime";
        }
        return t;
    }

    private static boolean parseBoolean(Object v, boolean def) {
        if (v == null) {
            return def;
        }
        String s = v.toString().trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return false;
        }
        return def;
    }

    private SqlStatement buildTableInsert(String tableId, String appId, String tenantCode, String entityName, String tableName, java.sql.Timestamp now) {
        String sql = "INSERT INTO platform_dev_table (" +
                "id, app_id, table_name, entity_name, title, table_type, " +
                "enable_status, del_status, create_at, update_at, tenant_code, " +
                "creator, creator_name, updater, updater_name" +
                ") VALUES (?, ?, ?, ?, ?, 'entity', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return new SqlStatement(sql,
                tableId,
                appId,
                tableName,
                entityName,
                entityName,
                ColumnDefault.ENABLE_STATUS_VALUE,
                ColumnDefault.DEL_STATUS_VALUE,
                now,
                now,
                tenantCode,
                OPERATOR,
                OPERATOR,
                OPERATOR,
                OPERATOR);
    }

    private SqlStatement buildInsert(String tableId, String appId, String tenantCode, String tableName, SchemaColumn sc, ColumnMeta cm) {
        java.sql.Timestamp now = new java.sql.Timestamp(new Date().getTime());
        // 主键用雪花算法（与平台一致，见 utils.UIDGenerator）
        String id = String.valueOf(UIDGenerator.generate());
        // 裸 SQL 绕过 ORM 审计拦截器，creator/updater 固定为工具身份 meta-sync（两列非空无默认值）
        String colName = sc.getColumnName().toLowerCase(Locale.ENGLISH);
        String fieldName = StringUtils.toCamelCase(colName);
        String title = StringUtils.isNotBlank(sc.getColumnComment()) ? sc.getColumnComment() : colName;
        String sql = "INSERT INTO platform_dev_column (" +
                "id, app_id, table_id, table_name, field_name, column_name, title, " +
                "data_type, column_type, is_nullable, is_unique, column_key, " +
                "character_maxinum_length, numeric_precision, numeric_scale, " +
                "ordinal_position, seq_no, enable_status, del_status, create_at, update_at, tenant_code, " +
                "creator, creator_name, updater, updater_name" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return new SqlStatement(sql,
                id,
                appId,
                StringUtils.isNotBlank(tableId) ? tableId : null,
                tableName,
                fieldName,
                colName,
                title,
                cm.getDataType() == null ? "" : cm.getDataType(),
                cm.getType() == null ? "" : cm.getType(),
                cm.isNullable() ? 1 : 0,
                cm.isUniqued() ? 1 : 0,
                cm.isKey() ? 1 : 0,
                cm.getCharMaxLength(),
                cm.getNumericPrecision(),
                cm.getNumericScale(),
                cm.getOrdinalPosition(),
                cm.getOrdinalPosition(),
                ColumnDefault.ENABLE_STATUS_VALUE,
                ColumnDefault.DEL_STATUS_VALUE,
                now,
                now,
                tenantCode,
                OPERATOR,
                OPERATOR,
                OPERATOR,
                OPERATOR);
    }

    /**
     * 构造 UPDATE：逐字段比较，只 SET 真正有差异的字段，全部走占位符绑定。
     * 精度/长度（charMaxLength/precision/scale）不自动补偿——显示宽度 MySQL 8 已废弃、
     * ColumnMeta 默认值与物理真实值天然不同，写回只会刷库制造噪音。
     */
    private SqlStatement buildUpdate(Map<String, Object> exist, String existId, ColumnMeta cm) {
        StringBuilder sets = new StringBuilder();
        List<Object> params = new ArrayList<>();
        String existDataType = exist.get("data_type") == null ? null : exist.get("data_type").toString();
        // 类型差异：data_type 与 column_type 一起更新为物理侧规范值
        if (existDataType != null && cm.getDataType() != null
                && !normalizeBaseType(existDataType).equals(normalizeBaseType(cm.getDataType()))) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append("data_type=?");
            params.add(cm.getDataType());
            if (cm.getType() != null) {
                sets.append(", column_type=?");
                params.add(cm.getType());
            }
        }
        // nullable 差异
        boolean existNullable = parseBoolean(exist.get("is_nullable"), true);
        if (existNullable != cm.isNullable()) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append("is_nullable=?");
            params.add(cm.isNullable() ? 1 : 0);
        }
        sets.append(" WHERE id=?");
        params.add(existId);
        return new SqlStatement("UPDATE platform_dev_column SET " + sets, params.toArray());
    }
}
