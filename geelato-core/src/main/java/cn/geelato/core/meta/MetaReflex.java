package cn.geelato.core.meta;


import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.gql.TypeConverter;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.lang.meta.*;
import cn.geelato.utils.DateUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by hongxueqian on 14-3-23.
 */
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class MetaReflex {

    /**
     * -- SETTER --
     * 如果在spring环境下，可以设置该值，以便可直接获取spring中已创建的bean，不需重新创建
     */
    @Setter
    private static ApplicationContext applicationContext;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);
    // 一些类型默认的长度
    public static HashedMap dataTypeDefaultMaxLengthMap = new HashedMap();

    static {
        // 最大长度255个字元(2^8-1)
        dataTypeDefaultMaxLengthMap.put("tinyText", 65535L);
        // 最大长度65535个字元(2^16-1)
        dataTypeDefaultMaxLengthMap.put("text", 65535L);
        // 最大长度 16777215 个字元(2^24-1)
        dataTypeDefaultMaxLengthMap.put("mediumText", 16777215L);
        // 最大长度4294967295个字元 (2^32-1)
        dataTypeDefaultMaxLengthMap.put("longText", 4294967295L);
    }

    private static Object getBean(Class clazz) {
        if (applicationContext == null) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
                log.error("创建对象失败！", e);
            }
            return null;
        } else {
            return applicationContext.getBean(clazz);
        }
    }

    /**
     * 循环向上转型, 获取对象的DeclaredFields
     * <p>
     * 如向上转型到Object仍无法找到, 返回null.
     */
    public static HashMap<String, Field> getAccessibleFields(final Object obj) {
        Assert.notNull(obj, "object can't be null");
        HashMap<String, Field> fieldMap = new HashMap<String, Field>();
        for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
            Field[] fields = superClass.getDeclaredFields();
            for (Field field : fields) {
                if (!fieldMap.containsKey(field.getName())) {
                    fieldMap.put(field.getName(), field);
                }
            }
        }
        return fieldMap;
    }

    public static TableMeta getTableMeta(final Object obj) {
        return getTableMeta(obj.getClass());
    }

    /**
     * 基于类的title注解，解析出表元数据
     */
    public static TableMeta getTableMeta(Class clazz) {
        Title title = (Title) clazz.getAnnotation(Title.class);
        return new TableMeta(getTableName(clazz), title != null ? title.title() : "", getEntityName(clazz), title != null ? title.description() : "");
    }

    /**
     * 根据给定的map生成TableMeta对象
     *
     * @param map 包含表元数据的map
     * @return 生成的TableMeta对象
     */
    public static TableMeta getTableMeta(Map map) {
        TableMeta tableMeta = new TableMeta(map);
        int delStatus = map.get("del_status") == null ? 0 : Integer.parseInt(map.get("del_status").toString());
        String id = map.get("id") == null ? null : map.get("id").toString();
        String title = StringUtils.hasText(tableMeta.getTitle()) ? tableMeta.getTitle() : (cn.geelato.utils.StringUtils.isEmpty(tableMeta.getTableName()) ? tableMeta.getEntityName() : tableMeta.getTableName());
        tableMeta.setId(id);
        tableMeta.setDelStatus(delStatus);
        tableMeta.setTitle(title);
        return tableMeta;
    }

    public static EntityMeta getEntityMeta(Class clazz) {
        EntityMeta em = new EntityMeta();
        em.setId(getId(clazz));
        em.setTableMeta(getTableMeta(clazz));
        em.setEntityName(em.getTableMeta().getEntityName());
        em.setEntityTitle(em.getTableMeta().getTitle());
        em.setEntityType(EntityType.Class);
        em.setClassType(clazz);

        Collection<TableForeign> tableForeigns = new ArrayList<>();
        HashMap<String, FieldMeta> map = getColumnFieldMetas(clazz, tableForeigns);
        em.setFieldMetas(map.values());
        em.setTableForeigns(tableForeigns);
        if (em.getFieldMetas() != null) {
            for (FieldMeta fm : em.getFieldMetas()) {
                fm.getColumnMeta().setTableName(em.getTableMeta().getTableName());
            }
        }
        em.setDictDataSourceMap(getDictDataSourceMap(clazz));
        return em;
    }

    // 方法重载，通过数据库读取的数据，构造EntityMeta
    public static EntityMeta getEntityMeta(Map tmap, List columnList) {
        EntityMeta em = new EntityMeta();
        em.setTableMeta(getTableMeta(tmap));
        em.setEntityName(tmap.get("entity_name").toString());
        em.setEntityTitle(em.getTableMeta().getTitle());

        HashMap<String, FieldMeta> map = getColumnFieldMetas(columnList);
        em.setFieldMetas(map.values());
        if (em.getFieldMetas() != null) {
            for (FieldMeta fm : em.getFieldMetas()) {
                fm.getColumnMeta().setTableName(em.getTableMeta().getTableName());
            }
        }
        em.setId(getPrimaryKey(map));
        return em;
    }

    public static EntityMeta getEntityMetaByTable(Map tmap, List columnList, List viewList,List checkList, List foreignList) {
        EntityMeta em = new EntityMeta();
        em.setTableMeta(getTableMeta(tmap));
        em.setEntityName(tmap.get("entity_name").toString());
        em.setEntityTitle(em.getTableMeta().getTitle());
        em.setEntityType(EntityType.Table);
        HashMap<String, FieldMeta> columnMap = getColumnFieldMetas(columnList);

        em.setFieldMetas(columnMap.values());
        HashMap<String, ViewMeta> viewMap = getViewMetas(viewList);
        em.setViewMetas(viewMap.values());

        List<TableCheck> checks = getTableCheckMetas(checkList);
        em.setTableChecks(checks);

        List<TableForeign> foreigns = getTableForeignMetas(foreignList);
        em.setTableForeigns(foreigns);
        em.setId(getPrimaryKey(columnMap));
        return em;
    }

    public static EntityMeta getEntityMetaByView(Map<String, Object> map) {
        EntityMeta em = new EntityMeta();
        TableMeta tableMeta = getTableMeta(map);
        String viewName = map.get("view_name").toString();
        tableMeta.setTableName(viewName);
        em.setTableMeta(tableMeta);
        em.setEntityName(viewName);
        em.setEntityTitle(map.get("title").toString());
        em.setEntityType(EntityType.View);
        String columnDataStr = map.get("view_column").toString();
        if (StringUtils.hasText(columnDataStr)) {
            List<Map<String, Object>> list = new ArrayList<>();
            JSONArray columnData = JSONArray.parse(columnDataStr);
            columnData.forEach(x -> {
                Map<String, Object> m = JSON.parseObject(x.toString(), Map.class);
                list.add(m);
            });
            HashMap<String, FieldMeta> columnMap = getColumnFieldMetas(list);
            em.setFieldMetas(columnMap.values());
            em.setId(getPrimaryKey(columnMap));
        }
        return em;
    }


    /**
     * 基于注解@Entity,按以下顺序获取，有值则返回：
     * table name 到 entity name 到 simple name of class
     *
     * @return 表名
     */
    public static String getTableName(Class clazz) {
        Entity entity = (Entity) clazz.getAnnotation(Entity.class);
        if (entity == null) {
            return clazz.getSimpleName();
        }
        if (StringUtils.hasText(entity.table())) {
            return entity.table();
        } else if (StringUtils.hasText(entity.name())) {
            return entity.name();
        } else {
            return clazz.getSimpleName();
        }
    }

    /**
     * 基于注解@Entity,按以下顺序获取，有值则返回：
     * model name -> name of class (with package name)
     *
     * @return 实体名
     */
    public static String getEntityName(Class clazz) {
        Entity entity = (Entity) clazz.getAnnotation(Entity.class);
        if (entity == null) {
            return clazz.getSimpleName();
        }
        if (StringUtils.hasText(entity.name())) {
            return entity.name();
        } else {
            return clazz.getName();
        }
    }


    public static FieldMeta getId(final Object obj) {
        Assert.notNull(obj, "object can't be null");
        return getId(obj.getClass());
    }

    public static FieldMeta getId(Class clazz) {
        for (Class<?> searchType = clazz; searchType != Object.class; searchType = searchType.getSuperclass()) {
            Method[] methods = searchType.getDeclaredMethods();
            for (Method method : methods) {
                Id id = method.getAnnotation(Id.class);
                if (id != null) {
                    String fieldName = method.getName().substring(3);
                    String firstChar = "" + fieldName.charAt(0);
                    fieldName = fieldName.replaceFirst(firstChar, firstChar.toLowerCase());
                    Title cn = method.getAnnotation(Title.class);
                    String title = cn != null ? (Strings.isEmpty(cn.title()) ? fieldName : cn.title()) : fieldName;
                    String columnName = fieldName;
                    Col col = method.getAnnotation(Col.class);
                    if (col != null) {
                        columnName = col.name();
                    }
                    return new FieldMeta(columnName, fieldName, title);
                }
            }
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                Id id = field.getAnnotation(Id.class);
                if (id != null) {
                    String fieldName = field.getName();
                    Title cn = field.getAnnotation(Title.class);
                    String title = cn != null ? (Strings.isEmpty(cn.title()) ? fieldName : cn.title()) : fieldName;
                    String columnName = fieldName;
                    Col col = field.getAnnotation(Col.class);
                    if (col != null) {
                        columnName = col.name();
                    }
                    return new FieldMeta(columnName, fieldName, title);
                }
            }
        }
        throw new RuntimeException("No @Id founded from " + clazz.getName() + "!");
    }

    public static HashMap<String, FieldMeta> getColumnFieldMetas(final Object obj) {
        Assert.notNull(obj, "object can't be null");
        return getColumnFieldMetas(obj.getClass());
    }

    /**
     * 解析get**方法或is**方法的映射，其它的，如set**方法不解析
     */
    public static HashMap<String, FieldMeta> getColumnFieldMetas(Class clazz) {
        return getColumnFieldMetas(clazz, null);
    }

    /**
     * 解析get**方法或is**方法的映射，其它的，如set**方法不解析
     *
     * @param tableForeigns 不为null时，解析表外键
     */
    public static HashMap<String, FieldMeta> getColumnFieldMetas(Class clazz, Collection<TableForeign> tableForeigns) {
        Object bean = getBean(clazz);
        HashMap<String, FieldMeta> map = new HashMap<>();
        List<String> transientProp = new ArrayList<>();
        for (Class<?> searchType = clazz; searchType != Object.class; searchType = searchType.getSuperclass()) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                try {
                    String fieldName = field.getName();
                    fieldName = firstCharToLow(fieldName);
                    if (!map.containsKey(fieldName) && !transientProp.contains(fieldName)) {
                        if (field.getAnnotation(Transient.class) == null) {
                            // 列，可能包括名为id的列
                            Col column = field.getAnnotation(Col.class);
                            Title cn = field.getAnnotation(Title.class);
                            String title = cn != null ? (Strings.isEmpty(cn.title()) ? fieldName : cn.title()) : fieldName;
                            String description = cn != null ? cn.description() : "";
                            FieldMeta cfm;
                            if (column != null && column.name() != null) {
                                cfm = new FieldMeta(column.name(), fieldName, title);

                                cfm.getColumnMeta().setNullable(column.nullable());
                                cfm.getColumnMeta().setUniqued(column.unique());
                                cfm.getColumnMeta().setName(column.name());
                                cfm.getColumnMeta().setNumericPrecision(column.numericPrecision());
                                cfm.getColumnMeta().setNumericScale(column.numericScale());
                                cfm.getColumnMeta().setRefColumn(column.isRefColumn());
                                cfm.getColumnMeta().setRefLocalCol(column.refLocalCol());
                                cfm.getColumnMeta().setRefColName(column.refColName());
                                cfm.getColumnMeta().setRefTables(column.refTables());
                                cfm.getColumnMeta().setCharMaxLength(column.charMaxlength() > 0 ?
                                        column.charMaxlength() : MapUtils.getLong(dataTypeDefaultMaxLengthMap, column.dataType(), 64L));
                                cfm.getColumnMeta().setDataType(column.dataType());
                                try {
                                    field.setAccessible(true);
                                    Object defaultValue = field.get(bean);
                                    if (defaultValue != null) {
                                        if (defaultValue instanceof Boolean) {
                                            cfm.getColumnMeta().setDefaultValue(Boolean.parseBoolean(defaultValue.toString()) ? "1" : "0");
                                        } else {
                                            cfm.getColumnMeta().setDefaultValue(String.valueOf(field.get(bean)));
                                        }
                                    }
                                } catch (IllegalAccessException e) {
                                    log.error("获取默认值失败:{}>{}", clazz.getName(), fieldName, e);
                                }

                                // 解析外键
                                if (tableForeigns != null) {
                                    ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
                                    if (foreignKey != null) {
                                        TableForeign tableForeign = new TableForeign();
                                        tableForeign.setMainTable(getEntityName(clazz));
                                        tableForeign.setMainTableCol(column.name());
                                        tableForeign.setForeignTable(getEntityName(foreignKey.fTable()));
                                        if (foreignKey.fCol().isEmpty()) {
                                            tableForeign.setForeignTableCol(getId(clazz).getColumnName());
                                        } else {
                                            tableForeign.setForeignTableCol(foreignKey.fCol());
                                        }
                                        tableForeigns.add(tableForeign);
                                    }
                                }
                                cfm.getColumnMeta().setDescription(description);
                                cfm.setFieldType(field.getType());
                                if (Strings.isEmpty(cfm.getColumnMeta().getDataType())) {
                                    cfm.getColumnMeta().setDataType(TypeConverter.toSqlTypeString(field.getType()));
                                }
                                cfm.getColumnMeta().afterSet();
                                map.put(fieldName, cfm);
                            }
                        } else {
                            transientProp.add(fieldName);
                        }
                    }
                } catch (RuntimeException e) {
                    log.error("解析{}失败！method:{}", clazz.getName(), field.getName());
                    throw e;
                }
            }
            Method[] methods = searchType.getDeclaredMethods();
            for (Method method : methods) {
                try {
                    if (!method.getName().startsWith("get") && !method.getName().startsWith("is")) {
                        continue;
                    }
                    String fieldName = "";
                    // 去掉get三个字符
                    if (method.getName().startsWith("get")) {
                        fieldName = method.getName().substring(3);
                    } else if (method.getName().startsWith("is")) {
                        fieldName = method.getName().substring(2);
                    }
                    // 首字符变小写
                    fieldName = firstCharToLow(fieldName);
                    if (!map.containsKey(fieldName) && !transientProp.contains(fieldName)) {
                        // 如果列中有@Transient，则跳过
                        if (method.getAnnotation(Transient.class) == null) {
                            // 列，可能包括名为id的列
                            Col column = method.getAnnotation(Col.class);
                            Title cn = method.getAnnotation(Title.class);
                            String title = cn != null ? (Strings.isEmpty(cn.title()) ? fieldName : cn.title()) : fieldName;
                            String description = cn != null ? cn.description() : "";
                            FieldMeta cfm = null;
                            if (column != null && column.name() != null) {
                                cfm = new FieldMeta(column.name(), fieldName, title);
                            } else {
                                cfm = new FieldMeta(fieldName, fieldName, title);
                            }
                            if (column != null) {
                                cfm.getColumnMeta().setNullable(column.nullable());
                                cfm.getColumnMeta().setUniqued(column.unique());
                                cfm.getColumnMeta().setName(column.name());
                                cfm.getColumnMeta().setNumericPrecision(column.numericPrecision());
                                cfm.getColumnMeta().setNumericScale(column.numericScale());
                                cfm.getColumnMeta().setRefColumn(column.isRefColumn());
                                cfm.getColumnMeta().setRefLocalCol(column.refLocalCol());
                                cfm.getColumnMeta().setRefColName(column.refColName());
                                cfm.getColumnMeta().setRefTables(column.refTables());
                                cfm.getColumnMeta().setCharMaxLength(column.charMaxlength() > 0 ?
                                        column.charMaxlength() : MapUtils.getLong(dataTypeDefaultMaxLengthMap, column.dataType(), 64L));
                                cfm.getColumnMeta().setDataType(column.dataType());
                                try {
                                    Object defaultValue = method.invoke(bean);
                                    if (defaultValue != null) {
                                        if (defaultValue instanceof Boolean) {
                                            cfm.getColumnMeta().setDefaultValue(Boolean.parseBoolean(defaultValue.toString()) ? "1" : "0");
                                        } else {
                                            cfm.getColumnMeta().setDefaultValue(String.valueOf(method.invoke(bean)));
                                        }
                                    }
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    log.error("获取默认值失败:{}>{}", clazz.getName(), fieldName, e);
                                }

                                // 解析外键
                                if (tableForeigns != null) {
                                    ForeignKey foreignKey = method.getAnnotation(ForeignKey.class);
                                    if (foreignKey != null) {
                                        TableForeign tableForeign = new TableForeign();
                                        tableForeign.setMainTable(getEntityName(clazz));
                                        tableForeign.setMainTableCol(column.name());
                                        tableForeign.setForeignTable(getEntityName(foreignKey.fTable()));
                                        if (foreignKey.fCol().isEmpty()) {
                                            tableForeign.setForeignTableCol(getId(clazz).getColumnName());
                                        } else {
                                            tableForeign.setForeignTableCol(foreignKey.fCol());
                                        }
                                        tableForeigns.add(tableForeign);
                                    }
                                }
                            }
                            cfm.getColumnMeta().setDescription(description);
                            cfm.setFieldType(method.getReturnType());
                            if (Strings.isEmpty(cfm.getColumnMeta().getDataType())) {
                                cfm.getColumnMeta().setDataType(TypeConverter.toSqlTypeString(method.getReturnType()));
                            }
                            cfm.getColumnMeta().afterSet();
                            map.put(fieldName, cfm);
                        } else {
                            transientProp.add(fieldName);
                        }
                    }
                } catch (RuntimeException e) {
                    log.error("解析{}失败！method:{}", clazz.getName(), method.getName());
                    throw e;
                }
            }

        }
        return map;
    }

    public static HashMap<String, ViewMeta> getViewMetas(List<HashMap> viewList) {
        HashMap<String, ViewMeta> map = new HashMap<String, ViewMeta>();
        if (viewList != null && !viewList.isEmpty()) {
            for (Map v_map : viewList) {
                try {
                    String viewName = v_map.get("view_name") == null ? null : v_map.get("view_name").toString();
                    String viewConstruct = v_map.get("view_construct") == null ? null : v_map.get("view_construct").toString();
                    String viewColumn = v_map.get("view_column") == null ? null : v_map.get("view_column").toString();
                    String viewType = v_map.get("view_type") == null ? null : v_map.get("view_type").toString();
                    String entityName = v_map.get("entity_name") == null ? null : v_map.get("entity_name").toString();
                    if (Strings.isNotBlank(viewName) && !map.containsKey(viewName)) {
                        ViewMeta vm = new ViewMeta(viewName, viewType, viewConstruct, viewColumn, entityName);
                        map.put(viewName, vm);
                        ViewManager.singleInstance().addViewMeta(viewName, vm);
                    }
                } catch (RuntimeException e) {
                    throw e;
                }
            }
        }

        return map;
    }

    public static HashMap<String, FieldMeta> getColumnFieldMetas(List<Map<String, Object>> columnList) {
        HashMap<String, FieldMeta> map = new HashMap<>();
        for (Map<String, Object> c_map : columnList) {
            String fieldName = c_map.get("field_name") == null ? null : c_map.get("field_name").toString();
            String title = c_map.get("title") == null ? null : c_map.get("title").toString();
            String columnName = c_map.get("column_name") == null ? null : c_map.get("column_name").toString();

            if (Strings.isNotBlank(fieldName) && !map.containsKey(fieldName)) {
                String selectType = c_map.get("select_type") == null ? null : c_map.get("select_type").toString().toUpperCase(Locale.ENGLISH);
                String typeExtra = c_map.get("type_extra") == null ? null : c_map.get("type_extra").toString();
                String extraValue = c_map.get("extra_value") == null ? null : c_map.get("extra_value").toString();
                String extraMap = c_map.get("extra_map") == null ? null : c_map.get("extra_map").toString();
                String dataType = c_map.get("data_type") == null ? null : c_map.get("data_type").toString().toUpperCase(Locale.ENGLISH);
                String defaultValue = c_map.get("column_default") == null ? null : c_map.get("column_default").toString();
                String comment = c_map.get("column_comment") == null ? null : c_map.get("column_comment").toString();
                Boolean enableStatus = c_map.get("enable_status") == null ? null : Boolean.parseBoolean(c_map.get("enable_status").toString());

                FieldMeta cfm = new FieldMeta(columnName, fieldName, title);
                cfm.getColumnMeta().setFieldName(fieldName);
                cfm.getColumnMeta().setUniqued(c_map.get("is_unique") != null && Boolean.parseBoolean(c_map.get("is_unique").toString()));
                cfm.getColumnMeta().setNullable(c_map.get("is_nullable") == null || Boolean.parseBoolean(c_map.get("is_nullable").toString()));
                cfm.getColumnMeta().setDefaultValue(Strings.isNotBlank(defaultValue) ? defaultValue : null);
                cfm.getColumnMeta().setDescription(c_map.get("description") == null ? null : c_map.get("description").toString());
                cfm.getColumnMeta().setType(c_map.get("column_type") == null ? null : c_map.get("column_type").toString());
                cfm.getColumnMeta().setTitle(title);
                if (c_map.get("character_maxinum_length") != null) {
                    cfm.getColumnMeta().setCharMaxLength(Long.parseLong(c_map.get("character_maxinum_length").toString()));
                }
                if (c_map.get("datetime_precision") != null) {
                    cfm.getColumnMeta().setDatetimePrecision(Integer.parseInt(c_map.get("datetime_precision").toString()));
                }
                cfm.getColumnMeta().setId(c_map.get("id") == null ? null : c_map.get("id").toString());
                cfm.getColumnMeta().setKey(c_map.get("column_key") != null && Boolean.parseBoolean(c_map.get("column_key").toString()));
                if (c_map.get("linked") != null) {
                    cfm.getColumnMeta().setLinked(Integer.parseInt(c_map.get("linked").toString()));
                }
                if (c_map.get("numeric_precision") != null) {
                    cfm.getColumnMeta().setNumericPrecision(Integer.parseInt(c_map.get("numeric_precision").toString()));
                }
                cfm.getColumnMeta().setNumericSigned(c_map.get("numeric_signed") != null && Boolean.parseBoolean(c_map.get("numeric_signed").toString()));
                cfm.getColumnMeta().setAutoIncrement(c_map.get("auto_increment") != null && Boolean.parseBoolean(c_map.get("auto_increment").toString()));
                cfm.getColumnMeta().setDataType(dataType);
                cfm.getColumnMeta().setSelectType(selectType);
                cfm.getColumnMeta().setTypeExtra(typeExtra);
                cfm.getColumnMeta().setExtraValue(extraValue);
                cfm.getColumnMeta().setExtraMap(extraMap);
                if (c_map.get("ordinal_position") != null) {
                    cfm.getColumnMeta().setOrdinalPosition(Integer.parseInt(c_map.get("ordinal_position").toString()));
                }
                cfm.getColumnMeta().setName(columnName);
                cfm.getColumnMeta().setTableId(c_map.get("table_id") == null ? null : c_map.get("table_id").toString());
                cfm.getColumnMeta().setTableName(c_map.get("table_name") == null ? null : c_map.get("table_name").toString());
                cfm.getColumnMeta().setComment(Strings.isNotBlank(comment) ? comment : title);
                if (c_map.get("numeric_scale") != null) {
                    cfm.getColumnMeta().setNumericScale(Integer.parseInt(c_map.get("numeric_scale").toString()));
                }
                if (c_map.get("del_status") != null) {
                    cfm.getColumnMeta().setDelStatus(Integer.parseInt(c_map.get("del_status").toString()));
                }
                cfm.getColumnMeta().setEnableStatus(Boolean.TRUE.equals(enableStatus) ? 1 : 0);
                cfm.getColumnMeta().setAutoName(c_map.get("auto_name") == null ? null : c_map.get("auto_name").toString());
                cfm.getColumnMeta().setAutoAdd(c_map.get("auto_add") != null && Boolean.parseBoolean(c_map.get("auto_add").toString()));
                cfm.getColumnMeta().setSynced(c_map.get("synced") != null && Boolean.parseBoolean(c_map.get("synced").toString()));
                cfm.getColumnMeta().setDrawed(c_map.get("drawed") != null && Boolean.parseBoolean(c_map.get("drawed").toString()));
                cfm.getColumnMeta().setEncrypted(c_map.get("encrypted") != null && Boolean.parseBoolean(c_map.get("encrypted").toString()));
                cfm.getColumnMeta().setMarker(c_map.get("marker") == null ? null : c_map.get("marker").toString());
                cfm.getColumnMeta().setTenantCode(c_map.get("tenant_code") == null ? null : c_map.get("tenant_code").toString());
                cfm.getColumnMeta().setAppId(c_map.get("app_id") == null ? null : c_map.get("app_id").toString());

                if (MysqlDataTypeEnum.getTexts().contains(dataType)) {
                    cfm.getColumnMeta().setDefaultValue(null);
                }
                cfm.setFieldType(MysqlToJavaEnum.getJava(dataType));
                map.put(fieldName, cfm);
            }
        }

        return map;
    }

    /**
     * 筛选出主键
     */
    public static FieldMeta getPrimaryKey(HashMap<String, FieldMeta> columnMap) {
        FieldMeta fieldMeta = null;
        if (columnMap != null && !columnMap.isEmpty()) {
            for (Map.Entry<String, FieldMeta> map : columnMap.entrySet()) {
                if (map.getValue().getColumnMeta().isKey()) {
                    fieldMeta = map.getValue();
                    break;
                }
            }
            if (fieldMeta == null) {
                fieldMeta = columnMap.get("id");
            }
        }

        return fieldMeta;
    }

    public static List<TableForeign> getTableForeignMetas(List<HashMap> foreignList) {
        List<TableForeign> foreigns = new ArrayList<>();
        if (foreignList != null && !foreignList.isEmpty()) {
            for (Map f_map : foreignList) {
                try {
                    int delStatus = f_map.get("del_status") == null ? 0 : Integer.parseInt(f_map.get("del_status").toString());
                    String id = f_map.get("id") == null ? null : f_map.get("id").toString();
                    TableForeign foreign = new TableForeign(f_map);
                    foreign.setId(id);
                    foreign.setDelStatus(delStatus);
                    foreigns.add(foreign);
                } catch (RuntimeException e) {
                    throw e;
                }
            }
        }

        return foreigns;
    }

    public static List<TableCheck> getTableCheckMetas(List<HashMap> checkList) {
        List<TableCheck> checks = new ArrayList<>();
        if (checkList != null && !checkList.isEmpty()) {
            for (Map f_map : checkList) {
                try {
                    int delStatus = f_map.get("del_status") == null ? 0 : Integer.parseInt(f_map.get("del_status").toString());
                    String id = f_map.get("id") == null ? null : f_map.get("id").toString();
                    TableCheck ck = new TableCheck(f_map);
                    ck.setId(id);
                    ck.setDelStatus(delStatus);
                    checks.add(ck);
                } catch (RuntimeException e) {
                    throw e;
                }
            }
        }

        return checks;
    }

    /**
     * 解析get**方法或is**方法的映射，并返回包含字典数据源信息的HashMap。
     * 该方法遍历指定类的所有方法（包括继承的方法），并解析以get或is开头的方法。
     * 对于每个符合条件的方法，它尝试从方法名中提取字段名，并检查该字段是否已经在HashMap中存在。
     * 如果不存在，则检查方法上是否有DictDataSrc注解。
     * 如果有，则创建一个DictDataSource对象，并根据注解中的信息设置其属性，然后将其添加到HashMap中。
     * 如果在解析过程中发生运行时异常，则记录错误日志并抛出异常。
     *
     * @param clazz 要解析的类
     * @return 包含字典数据源信息的HashMap，键为字段名，值为对应的DictDataSource对象
     */
    public static HashMap<String, DictDataSource> getDictDataSourceMap(Class clazz) {
        HashMap<String, DictDataSource> map = new HashMap<String, DictDataSource>();
        for (Class<?> searchType = clazz; searchType != Object.class; searchType = searchType.getSuperclass()) {
            Method[] methods = searchType.getDeclaredMethods();
            for (Method method : methods) {
                try {
                    String fieldName = getFieldNameByGetMethod(method.getName());
                    if (fieldName == null) {
                        continue;
                    }
                    if (!map.containsKey(fieldName)) {
                        DictDataSrc ds = method.getAnnotation(DictDataSrc.class);
                        if (ds != null) {
                            DictDataSource dds = new DictDataSource();
                            dds.setGroup(ds.group());
                            dds.setCode(ds.code());
                            map.put(fieldName, dds);
                        }
                    }
                } catch (RuntimeException e) {
                    throw e;
                }
            }

        }
        return map;
    }

    /**
     * 比较两个对象之间的属性值差异，并返回差异值的JSON字符串。
     *
     * @param before         要比较的第一个对象，不能为null。
     * @param after          要比较的第二个对象，不能为null，且应与before为相同类型。
     * @param ignoreFieldMap 包含需要忽略比较的字段名称和对应值的Map，如果某个字段需要被忽略，则将其名称和任意值添加到该Map中。
     * @return 返回描述对象间差异值的JSON字符串。如果两个对象相同或差异值为空，则返回空字符串。
     */
    public static String compareEntityValue(Object before, Object after, Map<String, String> ignoreFieldMap) {
        Assert.notNull(before, "不能为空");
        Assert.notNull(after, "不能为空");
        Assert.isTrue(before.getClass().equals(after.getClass()), "before与after为相同类型");
        HashMap<String, Field> beforeFieldHashMap = getAccessibleFields(before);
        if (beforeFieldHashMap.values().isEmpty()) {
            return "";
        }
        HashMap<String, Field> afterFieldHashMap = getAccessibleFields(after);
        StringBuilder jsonResult = new StringBuilder();
        jsonResult.append("[");
        for (Field field : beforeFieldHashMap.values()) {
            if (ignoreFieldMap != null && ignoreFieldMap.containsKey(field.getName())) {
                continue;
            }
            Field afterField = null;
            try {
                field.setAccessible(true);
                Object beforeValueObject = field.get(before);
                String beforeValue = beforeValueObject == null ? "" : (beforeValueObject instanceof Date ? DATE_FORMAT.format(beforeValueObject) : beforeValueObject.toString());

                afterField = afterFieldHashMap.get(field.getName());
                afterField.setAccessible(true);
                Object afterValueObject = afterField.get(after);
                String afterValue = afterValueObject == null ? "" : (afterValueObject instanceof Date ? DATE_FORMAT.format(afterValueObject) : afterValueObject.toString());
                if (!beforeValue.equals(afterValue)) {
                    jsonResult.append("{\"field\":\"");
                    jsonResult.append(field.getName());
                    jsonResult.append("\",\"from\":\"").append(beforeValue).append("\",\"to\":\"").append(afterValue).append("\"},");
                }
            } catch (IllegalAccessException e) {
                log.error("", e);
            } finally {
                field.setAccessible(false);
                if (afterField != null) {
                    afterField.setAccessible(true);
                }
            }
        }
        jsonResult.deleteCharAt(jsonResult.length() - 1);
        return jsonResult.append("]").toString();
    }


    private static String firstCharToLow(String str) {
        String firstChar = "" + str.charAt(0);
        return str.replaceFirst(firstChar, firstChar.toLowerCase());
    }

    private static String getFieldNameByGetMethod(String methodName) {
        if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
            return null;
        }
        String fieldName = "";
        // 去掉get三个字符
        if (methodName.startsWith("get")) {
            fieldName = methodName.substring(3);
        }
        if (methodName.startsWith("is")) {
            fieldName = methodName.substring(2);
        }
        // 首字符变小写
        return firstCharToLow(fieldName);
    }

}
