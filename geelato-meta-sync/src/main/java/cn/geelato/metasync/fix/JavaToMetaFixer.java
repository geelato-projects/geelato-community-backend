package cn.geelato.metasync.fix;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.metasync.core.MetaSourceLoader;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.UIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

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

    /** 补偿写库的创建者/更新者标识（裸 SQL 无 ORM 拦截器，固定为工具身份） */
    private static final String OPERATOR = "meta-sync";

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
     * @param appId 应用 id（界面指定，必填）
     * @param tenantCode 租户编码（界面指定，必填）
     * @param output "sql"=返回INSERT语句（不写库），"db"=写库
     */
    public FixResult genMeta(String entityName, String appId, String tenantCode, String output) {
        boolean apply = "db".equalsIgnoreCase(output);
        FixResult result = new FixResult(apply);
        EntityMeta em = loader.getJavaEntityByName(entityName);
        if (em == null) {
            log.warn("未找到 Java 类源实体：{}", entityName);
            return result;
        }
        JdbcTemplate jdbc = apply ? dao.getJdbcTemplate() : null;
        java.sql.Timestamp now = new java.sql.Timestamp(new Date().getTime());

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
            // 生成 platform_dev_table 的 INSERT（雪花 id；参数化，值不拼进 SQL 文本）
            tableId = String.valueOf(UIDGenerator.generate());
            String title = em.getEntityTitle() == null ? tableName : em.getEntityTitle();
            SqlStatement stmt = new SqlStatement(
                    "INSERT INTO platform_dev_table (id, app_id, entity_name, table_name, title, " +
                            "table_type, enable_status, del_status, create_at, update_at, tenant_code, creator, creator_name, updater, updater_name) " +
                            "VALUES (?, ?, ?, ?, ?, 'entity', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tableId, appId, entityName, tableName, title,
                    ColumnDefault.ENABLE_STATUS_VALUE, ColumnDefault.DEL_STATUS_VALUE,
                    now, now, tenantCode, OPERATOR, OPERATOR, OPERATOR, OPERATOR);
            result.previewSql.add(stmt.toPreview());
            if (apply) {
                jdbc.update(stmt.getSql(), stmt.getParams());
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
                String id = String.valueOf(UIDGenerator.generate());
                String fieldName = StringUtils.isNotBlank(fm.getFieldName()) ? fm.getFieldName() : cn.geelato.utils.StringUtils.toCamelCase(colName.toLowerCase());
                String dataType = cm.getDataType() == null ? "" : cm.getDataType();
                String title = StringUtils.isNotBlank(cm.getTitle()) ? cm.getTitle() : colName;
                SqlStatement stmt = new SqlStatement(
                        "INSERT INTO platform_dev_column (" +
                                "id, app_id, table_id, table_name, field_name, column_name, title, " +
                                "data_type, is_nullable, column_key, character_maxinum_length, " +
                                "numeric_precision, numeric_scale, ordinal_position, seq_no, enable_status, del_status, create_at, update_at, tenant_code, " +
                                "creator, creator_name, updater, updater_name" +
                                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, appId, tableId, tableName, fieldName, colName, title,
                        dataType, cm.isNullable() ? 1 : 0, cm.isKey() ? 1 : 0, cm.getCharMaxLength(),
                        cm.getNumericPrecision(), cm.getNumericScale(), cm.getOrdinalPosition(), cm.getOrdinalPosition(),
                        ColumnDefault.ENABLE_STATUS_VALUE, ColumnDefault.DEL_STATUS_VALUE, now, now, tenantCode,
                        OPERATOR, OPERATOR, OPERATOR, OPERATOR);
                result.previewSql.add(stmt.toPreview());
                if (apply) {
                    jdbc.update(stmt.getSql(), stmt.getParams());
                    result.executed++;
                }
            }
        }
        return result;
    }
}
