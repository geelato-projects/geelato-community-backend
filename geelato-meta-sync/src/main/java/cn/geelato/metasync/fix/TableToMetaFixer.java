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
                // update（仅当 dataType/column_type 不一致时）
                Map<String, Object> exist = existing.get(colLower);
                if (needUpdate(exist, cm)) {
                    String existId = exist.get("id") == null ? null : exist.get("id").toString();
                    String sql = buildUpdateSql(existId, cm);
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

    private boolean needUpdate(Map<String, Object> exist, ColumnMeta cm) {
        Object existType = exist.get("column_type");
        String existDataType = exist.get("data_type") == null ? null : exist.get("data_type").toString();
        if (existType != null && !existType.toString().equalsIgnoreCase(cm.getType())) {
            return true;
        }
        if (existDataType != null && cm.getDataType() != null && !existDataType.equalsIgnoreCase(cm.getDataType())) {
            return true;
        }
        return false;
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

    private String buildUpdateSql(String existId, ColumnMeta cm) {
        StringBuilder sb = new StringBuilder("UPDATE platform_dev_column SET ");
        sb.append("data_type='").append(cm.getDataType() == null ? "" : cm.getDataType()).append("', ");
        sb.append("column_type='").append(cm.getType() == null ? "" : escape(cm.getType())).append("', ");
        sb.append("is_nullable=").append(cm.isNullable() ? 1 : 0).append(", ");
        sb.append("character_maxinum_length=").append(cm.getCharMaxLength()).append(", ");
        sb.append("numeric_precision=").append(cm.getNumericPrecision()).append(", ");
        sb.append("numeric_scale=").append(cm.getNumericScale()).append(" ");
        sb.append("WHERE id='").append(existId).append("'");
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
