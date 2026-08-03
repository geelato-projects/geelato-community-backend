package cn.geelato.metasync.fix;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.metasync.core.MetaSourceLoader;
import cn.geelato.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Java 类 → 实体定义（platform_dev_table/column）补偿。
 * <p>
 * 把 Java 类反射出的实体定义补全到 platform_dev_table / platform_dev_column。
 * output=sql 时返回 INSERT 语句列表（不写库）；output=db 时写库。
 * 通过 {@link MetaSourceLoader} 直接 IO 获取 Java 类源（不依赖 MetaManager 全局缓存）。
 *
 * @author geemeta
 */
public class JavaToMetaFixer {

    private static final Logger log = LoggerFactory.getLogger(JavaToMetaFixer.class);

    private final Dao dao;
    private final MetaSourceLoader loader;

    public JavaToMetaFixer(Dao dao, MetaSourceLoader loader) {
        this.dao = dao;
        this.loader = loader;
    }

    public static class FixResult {
        /** 预览 SQL 语句 */
        public List<String> previewSql = new ArrayList<>();
        /** 实际执行条数 */
        public int executed;
        public boolean applied;

        public FixResult(boolean applied) {
            this.applied = applied;
        }
    }

    /**
     * @param entityName Java 实体的 entityName（= @Entity(name=...) 或类全限定名）
     * @param output "sql"=返回INSERT语句（不写库），"db"=写库
     */
    public FixResult genMeta(String entityName, String output) {
        boolean apply = "db".equalsIgnoreCase(output);
        FixResult result = new FixResult(apply);
        EntityMeta em = loader.getJavaEntityByName(entityName);
        if (em == null) {
            log.warn("未找到 Java 类源实体：{}", entityName);
            return result;
        }
        JdbcTemplate jdbc = apply ? dao.getJdbcTemplate() : null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = sdf.format(new Date());

        TableMeta tm = em.getTableMeta();
        String tableName = tm != null && StringUtils.isNotBlank(tm.getTableName()) ? tm.getTableName() : em.getTableName();
        if (StringUtils.isBlank(tableName)) {
            tableName = entityName;
        }

        // 1. platform_dev_table 是否已存在
        String tableId = null;
        if (jdbc != null) {
            List<java.util.Map<String, Object>> t = jdbc.queryForList(
                    "SELECT id FROM platform_dev_table WHERE entity_name = ? AND del_status = ? LIMIT 1",
                    entityName, ColumnDefault.DEL_STATUS_VALUE);
            if (t != null && !t.isEmpty() && t.get(0).get("id") != null) {
                tableId = t.get(0).get("id").toString();
            }
        }
        if (StringUtils.isBlank(tableId)) {
            // 生成 platform_dev_table 的 INSERT
            tableId = java.util.UUID.randomUUID().toString().replace("-", "");
            String title = em.getEntityTitle() == null ? tableName : escape(em.getEntityTitle());
            String sql = "INSERT INTO platform_dev_table (id, entity_name, table_name, title, " +
                    "table_type, enable_status, del_status, create_at, update_at) VALUES (" +
                    "'" + tableId + "', '" + escape(entityName) + "', '" + escape(tableName) + "', '" + title + "', " +
                    "'entity', " + ColumnDefault.ENABLE_STATUS_VALUE + ", " + ColumnDefault.DEL_STATUS_VALUE + ", " +
                    "'" + now + "', '" + now + "')";
            result.previewSql.add(sql);
            if (apply) {
                jdbc.execute(sql);
                result.executed++;
            }
        }

        // 2. platform_dev_column 字段
        Collection<FieldMeta> fieldMetas = em.getFieldMetas();
        if (fieldMetas != null) {
            for (FieldMeta fm : fieldMetas) {
                if (fm == null || fm.getColumnMeta() == null) {
                    continue;
                }
                ColumnMeta cm = fm.getColumnMeta();
                String colName = StringUtils.isNotBlank(cm.getName()) ? cm.getName() : fm.getColumnName();
                if (StringUtils.isBlank(colName)) {
                    continue;
                }
                // db 模式：检查是否已存在该列
                if (apply) {
                    List<java.util.Map<String, Object>> c = jdbc.queryForList(
                            "SELECT id FROM platform_dev_column WHERE table_id = ? AND column_name = ? AND del_status = ? LIMIT 1",
                            tableId, colName, ColumnDefault.DEL_STATUS_VALUE);
                    if (c != null && !c.isEmpty()) {
                        continue; // 已存在，跳过
                    }
                }
                String id = java.util.UUID.randomUUID().toString().replace("-", "");
                String fieldName = StringUtils.isNotBlank(fm.getFieldName()) ? fm.getFieldName() : cn.geelato.utils.StringUtils.toCamelCase(colName.toLowerCase());
                String dataType = cm.getDataType() == null ? "" : cm.getDataType();
                String title = StringUtils.isNotBlank(cm.getTitle()) ? escape(cm.getTitle()) : colName;
                StringBuilder sb = new StringBuilder("INSERT INTO platform_dev_column (");
                sb.append("id, table_id, table_name, field_name, column_name, title, ");
                sb.append("data_type, is_nullable, column_key, character_maxinum_length, ");
                sb.append("numeric_precision, numeric_scale, ordinal_position, seq_no, enable_status, del_status, create_at, update_at");
                sb.append(") VALUES (");
                sb.append("'").append(id).append("', ");
                sb.append("'").append(tableId).append("', ");
                sb.append("'").append(escape(tableName)).append("', ");
                sb.append("'").append(escape(fieldName)).append("', ");
                sb.append("'").append(escape(colName)).append("', ");
                sb.append("'").append(title).append("', ");
                sb.append("'").append(dataType).append("', ");
                sb.append(cm.isNullable() ? 1 : 0).append(", ");
                sb.append(cm.isKey() ? 1 : 0).append(", ");
                sb.append(cm.getCharMaxLength()).append(", ");
                sb.append(cm.getNumericPrecision()).append(", ");
                sb.append(cm.getNumericScale()).append(", ");
                sb.append(cm.getOrdinalPosition()).append(", ");
                sb.append(cm.getOrdinalPosition()).append(", ");
                sb.append(ColumnDefault.ENABLE_STATUS_VALUE).append(", ");
                sb.append(ColumnDefault.DEL_STATUS_VALUE).append(", ");
                sb.append("'").append(now).append("', ");
                sb.append("'").append(now).append("')");
                result.previewSql.add(sb.toString());
                if (apply) {
                    jdbc.execute(sb.toString());
                    result.executed++;
                }
            }
        }
        return result;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
