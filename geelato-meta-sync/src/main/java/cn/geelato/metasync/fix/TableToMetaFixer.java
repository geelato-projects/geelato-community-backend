package cn.geelato.metasync.fix;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.StringUtils;
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
     *
     * @param tableName 物理表名
     * @param entityName 对应的实体名（用于回填 platform_dev_column.table_name / entityName 关联，可为空则用 tableName）
     * @param tableId platform_dev_table.id（用于回填 column.table_id；为空则尝试按 entity_name 查）
     * @param apply true=写库，false=仅返回预览 SQL
     */
    public FixResult syncTableToMeta(String tableName, String entityName, String tableId, boolean apply) {
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

        // 2. 解析 tableId（未提供则按 entity_name 查 platform_dev_table）
        if (StringUtils.isBlank(tableId)) {
            List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT id FROM platform_dev_table WHERE entity_name = ? AND del_status = ? LIMIT 1",
                    entityName, ColumnDefault.DEL_STATUS_VALUE);
            if (t != null && !t.isEmpty() && t.get(0).get("id") != null) {
                tableId = t.get(0).get("id").toString();
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
                String sql = buildInsertSql(tableId, entityName, tableName, sc, cm);
                result.previewSql.add(sql);
                if (apply) {
                    jdbc.execute(sql);
                    result.executed++;
                }
            } else {
                // update（仅当归一化基础类型或 nullable 有真实差异时；格式差异忽略）
                Map<String, Object> exist = existing.get(colLower);
                if (needUpdate(exist, cm)) {
                    String existId = exist.get("id") == null ? null : exist.get("id").toString();
                    String sql = buildUpdateSql(exist, existId, cm);
                    result.previewSql.add(sql);
                    if (apply) {
                        jdbc.execute(sql);
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

    private String buildInsertSql(String tableId, String entityName, String tableName, SchemaColumn sc, ColumnMeta cm) {
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        String colName = sc.getColumnName().toLowerCase(Locale.ENGLISH);
        String fieldName = StringUtils.toCamelCase(colName);
        String title = StringUtils.isNotBlank(sc.getColumnComment()) ? escape(sc.getColumnComment()) : colName;
        StringBuilder sb = new StringBuilder("INSERT INTO platform_dev_column (");
        sb.append("id, app_id, table_id, table_name, field_name, column_name, title, ");
        sb.append("data_type, column_type, is_nullable, is_unique, column_key, ");
        sb.append("character_maxinum_length, numeric_precision, numeric_scale, ");
        sb.append("ordinal_position, seq_no, enable_status, del_status, create_at, update_at, tenant_code");
        sb.append(") VALUES (");
        sb.append("'").append(id).append("', ");
        sb.append("NULL, ");
        sb.append(StringUtils.isNotBlank(tableId) ? "'" + tableId + "'" : "NULL").append(", ");
        sb.append("'").append(escape(tableName)).append("', ");
        sb.append("'").append(escape(fieldName)).append("', ");
        sb.append("'").append(escape(colName)).append("', ");
        sb.append("'").append(title).append("', ");
        sb.append("'").append(cm.getDataType() == null ? "" : cm.getDataType()).append("', ");
        sb.append("'").append(cm.getType() == null ? "" : escape(cm.getType())).append("', ");
        sb.append(cm.isNullable() ? "1" : "0").append(", ");
        sb.append(cm.isUniqued() ? "1" : "0").append(", ");
        sb.append(cm.isKey() ? "1" : "0").append(", ");
        sb.append(cm.getCharMaxLength()).append(", ");
        sb.append(cm.getNumericPrecision()).append(", ");
        sb.append(cm.getNumericScale()).append(", ");
        sb.append(cm.getOrdinalPosition()).append(", ");
        sb.append(cm.getOrdinalPosition()).append(", ");
        sb.append(ColumnDefault.ENABLE_STATUS_VALUE).append(", ");
        sb.append(ColumnDefault.DEL_STATUS_VALUE).append(", ");
        sb.append("'").append(now).append("', ");
        sb.append("'").append(now).append("', ");
        sb.append("NULL");
        sb.append(")");
        return sb.toString();
    }

    /**
     * 构造 UPDATE：逐字段比较，只 SET 真正有差异的字段。
     * 精度/长度（charMaxLength/precision/scale）不自动补偿——显示宽度 MySQL 8 已废弃、
     * ColumnMeta 默认值与物理真实值天然不同，写回只会刷库制造噪音。
     */
    private String buildUpdateSql(Map<String, Object> exist, String existId, ColumnMeta cm) {
        StringBuilder sets = new StringBuilder();
        String existDataType = exist.get("data_type") == null ? null : exist.get("data_type").toString();
        // 类型差异：data_type 与 column_type 一起更新为物理侧规范值
        if (existDataType != null && cm.getDataType() != null
                && !normalizeBaseType(existDataType).equals(normalizeBaseType(cm.getDataType()))) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append("data_type='").append(cm.getDataType()).append("'");
            if (cm.getType() != null) {
                sets.append(", column_type='").append(escape(cm.getType())).append("'");
            }
        }
        // nullable 差异
        boolean existNullable = parseBoolean(exist.get("is_nullable"), true);
        if (existNullable != cm.isNullable()) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append("is_nullable=").append(cm.isNullable() ? 1 : 0);
        }
        return "UPDATE platform_dev_column SET " + sets + " WHERE id='" + existId + "'";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
