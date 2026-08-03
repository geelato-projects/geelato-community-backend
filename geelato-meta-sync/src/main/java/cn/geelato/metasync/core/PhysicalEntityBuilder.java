package cn.geelato.metasync.core;

import cn.geelato.core.meta.EntityType;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把物理表结构（{@link SchemaColumn} 列表）组装成 {@link EntityMeta}，供三方对比用。
 *
 * @author geemeta
 */
public class PhysicalEntityBuilder {

    /**
     * 由一个物理表的 SchemaColumn 列表构造 EntityMeta。
     *
     * @param tableName 物理表名（作为 entityName 与 tableName）
     * @param schemaColumns 物理列集合
     * @return EntityMeta（entityType=Table，物理来源）
     */
    public static EntityMeta build(String tableName, List<SchemaColumn> schemaColumns) {
        EntityMeta em = new EntityMeta();
        em.setEntityName(tableName);
        em.setEntityTitle(tableName);
        em.setEntityType(EntityType.Table);
        em.setCatalog("none");

        TableMeta tm = new TableMeta();
        tm.setEntityName(tableName);
        tm.setTableName(tableName);
        tm.setTitle(tableName);
        em.setTableMeta(tm);

        // 构造字段集合，主键由 columnKey=PRI 判定
        FieldMeta idMeta = null;
        Map<String, FieldMeta> fieldMap = new LinkedHashMap<>();
        for (SchemaColumn sc : schemaColumns) {
            ColumnMeta cm = sc.convertIntoMeta(null);
            cm.setTableName(tableName);
            String columnName = StringUtils.isNotBlank(cm.getName()) ? cm.getName() : sc.getColumnName();
            String fieldName = StringUtils.isNotBlank(cm.getFieldName())
                    ? cm.getFieldName()
                    : StringUtils.toCamelCase(columnName.toLowerCase(Locale.ENGLISH));
            FieldMeta fm = new FieldMeta(columnName, fieldName, cm.getTitle());
            fm.getColumnMeta().setDataType(cm.getDataType());
            fm.getColumnMeta().setName(columnName);
            fm.setFieldType(cn.geelato.core.enums.MysqlToJavaEnum.getJava(cm.getDataType()));
            // 把完整 ColumnMeta 放入（convertIntoMeta 已 afterSet）
            setColumnMeta(fm, cm);
            fieldMap.put(columnName, fm);
            if (cm.isKey()) {
                idMeta = fm;
            }
        }
        em.setFieldMetas(new ArrayList<>(fieldMap.values()));
        if (idMeta != null) {
            em.setId(idMeta);
        }
        return em;
    }

    /** 把 SchemaColumn 转出的完整 ColumnMeta 覆盖回 FieldMeta 内嵌的 ColumnMeta */
    private static void setColumnMeta(FieldMeta fm, ColumnMeta full) {
        ColumnMeta inner = fm.getColumnMeta();
        inner.setType(full.getType());
        inner.setNullable(full.isNullable());
        inner.setUniqued(full.isUniqued());
        inner.setCharMaxLength(full.getCharMaxLength());
        inner.setNumericPrecision(full.getNumericPrecision());
        inner.setNumericScale(full.getNumericScale());
        inner.setKey(full.isKey());
    }
}
