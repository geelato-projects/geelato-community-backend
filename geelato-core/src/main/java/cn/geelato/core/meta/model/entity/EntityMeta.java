package cn.geelato.core.meta.model.entity;


import cn.geelato.core.meta.DictDataSource;
import cn.geelato.core.meta.EntityType;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.SimpleFieldMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.Assert;

import java.util.*;

/**
 * @author geelato
 */
@SuppressWarnings("rawtypes")
public class EntityMeta {

    /**
     * -- GETTER --
     * 对于基于java类解析的实体，则返回类名（不包括包名）
     * 对于基于页面配置的实体，则返回配置的实体名称
     */
    // 实体的编码，如：user_info
    @Setter
    @Getter
    private String entityName;
    // 实体的中文名称，如：用户信息
    @Setter
    @Getter
    private String entityTitle;
    @Setter
    @Getter
    private Class classType;
    /**
     * -- GETTER --
     * 对于基于java类解析的实体，则有具体的类类型
     * 对于基于页面配置的实体，则返回值为空
     */
    @Setter
    @Getter
    private EntityType entityType;
    /**
     * -- GETTER --
     * 基于@Id获取实体中的主键字段名
     */
    @Getter
    private FieldMeta id;
    @Setter
    @Getter
    private TableMeta tableMeta;
    @Getter
    @Setter
    private String tableAlias;
    @Getter
    private String[] fieldNames;
    @Getter
    private Collection<FieldMeta> fieldMetas;
    @Setter
    @Getter
    private Collection<ViewMeta> viewMetas;
    @Getter
    private Collection<TableForeign> tableForeigns;
    @Getter
    @Setter
    private Collection<TableCheck> tableChecks;
    @Getter
    @Setter
    private Map<String, DictDataSource> dictDataSourceMap;
    // 冗余，用于快速获取列信息
    private LinkedHashMap<String, FieldMeta> fieldMetaMap;
    // 冗余，用于快速获取模型视图信息
    private LinkedHashMap<String, ViewMeta> viewMetaMap;
    // 冗余，用于快速获取列元数据，json格式，用于对外展示，过滤掉了一些数据库字段
    private LinkedHashMap<String, SimpleFieldMeta> simpleFieldMetaMap;
    // 冗余，用于快速获取外键关系
    @Getter
    private final Map<String, TableForeign> tableForeignsMap = new HashMap<>();
    // 不更新的字段
    private final Map<String, Boolean> ignoreUpdateFieldMap;

    public EntityMeta() {
        ignoreUpdateFieldMap = new HashMap<>();
        ignoreUpdateFieldMap.put("create_at", true);
        ignoreUpdateFieldMap.put("createAt", true);
        ignoreUpdateFieldMap.put("creator", true);
    }

    public void setId(FieldMeta id) {
        this.id = id;
        if (id != null) {
            ignoreUpdateFieldMap.put(id.getFieldName(), true);
        }
    }

    public void setFieldMetas(Collection<FieldMeta> fieldMetas) {
        if (fieldMetas == null) {
            return;
        }
        FieldMeta idMeta = null;
        FieldMeta titleMeta = null;
        FieldMeta nameMeta = null;
        FieldMeta createAtMeta = null;
        FieldMeta creatorMeta = null;
        FieldMeta updateAtMeta = null;
        FieldMeta updaterMeta = null;
        FieldMeta delStatusMeta = null;
        FieldMeta seqNoMeta = null;
        FieldMeta descriptionMeta = null;
        Collection<FieldMeta> nonSpecialFiledMetas = new ArrayList<>();

        for (FieldMeta fm : fieldMetas) {
            fm.setEntityMeta(this);
            switch (fm.getColumnName()) {
                case "id":
                    idMeta = fm;
                    break;
                case "title":
                    titleMeta = fm;
                    break;
                case "name":
                    nameMeta = fm;
                    break;
                case "seq_no":
                    seqNoMeta = fm;
                    break;
                case "del_status":
                    delStatusMeta = fm;
                    break;
                case "description":
                    descriptionMeta = fm;
                    break;
                case "create_at":
                    createAtMeta = fm;
                    break;
                case "creator":
                    creatorMeta = fm;
                    break;
                case "update_at":
                    updateAtMeta = fm;
                    break;
                case "updater":
                    updaterMeta = fm;
                    break;
                default:
                    nonSpecialFiledMetas.add(fm);
            }
        }

        if (fieldMetaMap == null) {
            fieldMetaMap = new LinkedHashMap<>(fieldMetas.size());
        }
        if (simpleFieldMetaMap == null) {
            simpleFieldMetaMap = new LinkedHashMap<>(fieldMetas.size());
        }
        // 这里不初始具体的size，后续程序需依据动态增加的size进行设值
        this.fieldMetas = new ArrayList<>();
        this.fieldNames = new String[fieldMetas.size()];
        int index = 0;
        // 先排前面的几个常用字段
        index = addToThisFieldMetasIfNotNull(idMeta, index);
        index = addToThisFieldMetasIfNotNull(titleMeta, index);
        index = addToThisFieldMetasIfNotNull(nameMeta, index);
        // 再排业务字段
        for (FieldMeta fm : nonSpecialFiledMetas) {
            this.fieldMetas.add(fm);
            fieldMetaMap.put(fm.getFieldName(), fm);
            this.fieldNames[index++] = fm.getFieldName();
            simpleFieldMetaMap.put(fm.getFieldName(), getSimpleFiledMeta(fm));
        }
        // 最后排其它常用字段
        index = addToThisFieldMetasIfNotNull(seqNoMeta, index);
        index = addToThisFieldMetasIfNotNull(descriptionMeta, index);
        index = addToThisFieldMetasIfNotNull(delStatusMeta, index);
        index = addToThisFieldMetasIfNotNull(createAtMeta, index);
        index = addToThisFieldMetasIfNotNull(creatorMeta, index);
        index = addToThisFieldMetasIfNotNull(updateAtMeta, index);
        index = addToThisFieldMetasIfNotNull(updaterMeta, index);
    }

    private int addToThisFieldMetasIfNotNull(FieldMeta fm, int index) {
        if (fm != null) {
            this.fieldMetas.add(fm);
            fieldMetaMap.put(fm.getFieldName(), fm);
            this.fieldNames[index] = fm.getFieldName();
            simpleFieldMetaMap.put(fm.getFieldName(), getSimpleFiledMeta(fm));
            return index + 1;
        }
        return index;
    }

    private SimpleFieldMeta getSimpleFiledMeta(FieldMeta fm) {
        SimpleFieldMeta meta = new SimpleFieldMeta();
        meta.setName(fm.getFieldName());
        meta.setType(fm.getFieldType().getSimpleName());
        meta.setTitle(fm.getColumnMeta().getTitle());
        meta.setComment(fm.getColumnMeta().getComment());
        meta.setNullable(fm.getColumnMeta().isNullable());
        meta.setCharMaxLength(fm.getColumnMeta().getCharMaxLength());
        meta.setPrecision(fm.getColumnMeta().getNumericPrecision());
        meta.setScale(fm.getColumnMeta().getNumericScale());
        meta.setDefaultValue(fm.getColumnMeta().getDefaultValue());
        meta.setSelectType(fm.getColumnMeta().getSelectType());
        meta.setTypeExtra(fm.getColumnMeta().getTypeExtra());
        meta.setExtraValue(fm.getColumnMeta().getExtraValue());
        meta.setExtraMap(fm.getColumnMeta().getExtraMap());
        return meta;
    }

    public String getTableName() {
        return tableMeta.getTableName();
    }

    public String getTableSchema() {
        return tableMeta.getTableSchema();
    }

    public String getColumnName(String fieldName) {
        FieldMeta fm = getFieldMeta(fieldName);
        Assert.notNull(fm, "获取不到元数据，fieldName：" + fieldName);
        return getFieldMeta(fieldName).getColumnName();
    }

    public FieldMeta getFieldMeta(String fieldName) {
        return fieldMetaMap.get(fieldName);
    }

    public FieldMeta[] getFieldMetas(String[] fieldNames) {
        FieldMeta[] fieldMetas = new FieldMeta[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            fieldMetas[i] = fieldMetaMap.get(fieldNames[i]);
        }
        return fieldMetas;
    }

    /**
     * 过滤掉数据库表名等信息，用于对外发布元数据服务的字段信息
     *
     * @param fieldNames 指定需获取元数据的字段
     */
    public SimpleFieldMeta[] getSimpleFieldMetas(String[] fieldNames) {
        SimpleFieldMeta[] metas = new SimpleFieldMeta[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            metas[i] = simpleFieldMetaMap.get(fieldNames[i]);
        }
        return metas;
    }

    /**
     * 过滤掉数据库表名等信息，用于对外发布元数据服务的字段信息
     */
    public Collection<SimpleFieldMeta> getAllSimpleFieldMetas() {
        return simpleFieldMetaMap.values();
    }

    public boolean containsField(String fieldName) {
        return fieldMetaMap.containsKey(fieldName);
    }

    public FieldMeta getFieldMetaByColumn(String columnName) {
        if (this.fieldMetas == null || this.fieldMetas.isEmpty()) {
            throw new RuntimeException("FieldMetas is Null");
        }
        for (FieldMeta fm : fieldMetas) {
            if (fm.getColumnName().equals(columnName)) {
                return fm;
            }
        }
        throw new RuntimeException("unfound FieldMeta "+ columnName);
    }

    public boolean isIgnoreUpdateField(String field) {
        return ignoreUpdateFieldMap.containsKey(field);
    }

    public void setTableForeigns(Collection<TableForeign> tableForeigns) {
        this.tableForeigns = tableForeigns;
        for (TableForeign t : tableForeigns) {
            tableForeignsMap.put(t.getForeignTable(), t);
        }
    }

    public ViewMeta getViewMeta(String viewName) {
        if (this.viewMetas == null || this.viewMetas.isEmpty()) {
            return null;
        }
        for (ViewMeta vm : viewMetas) {
            if (vm.getViewName().equals(viewName)) {
                return vm;
            }
        }
        return null;
    }

}
