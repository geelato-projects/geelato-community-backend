package cn.geelato.core.meta;


import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.mql.TypeConverter;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.lang.meta.*;
import cn.geelato.utils.DateUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.logging.log4j.util.Strings;
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

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);
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
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            log.error("init meta class fail!", ex);
        }
        return null;
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
     * 基于类的title注解，解析出表元数据。
     * <p>
     * 仅回填 {@code @Entity(connectId)} 的显式指定值到 TableMeta.connectId（扫描期安全，不依赖外部注入）。
     * {@code @Entity(catalog)} 的数据源映射不在此处解析——扫描期 catalogConnectIdMapping 可能尚未注入，
     * 改由运行时 {@link MetaManager#resolveConnectId(String)} 在查询期即时解析，规避时序问题。
     */
    public static TableMeta getTableMeta(Class clazz) {
        Title title = (Title) clazz.getAnnotation(Title.class);
        TableMeta tableMeta = new TableMeta(getTableName(clazz), title != null ? title.title() : "", getEntityName(clazz), title != null ? title.description() : "");
        Entity entity = (Entity) clazz.getAnnotation(Entity.class);
        if (entity != null && StringUtils.hasText(entity.connectId())) {
            tableMeta.setConnectId(entity.connectId());
        }
        return tableMeta;
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
        Entity entity = (Entity) clazz.getAnnotation(Entity.class);
        String catalog = entity != null && StringUtils.hasText(entity.catalog()) ? entity.catalog() : "none";
        em.setCatalog(catalog);

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

    public static EntityMeta getEntityMetaByTable(TableMeta tableMeta, List<ColumnMeta> columnList,
                                                  List<TableView> viewList, List<TableCheck> checkList,
                                                  List<TableForeign> foreignList) {
        EntityMeta em = new EntityMeta();
        em.setTableMeta(tableMeta);
        em.setEntityName(tableMeta.getEntityName());
        em.setEntityTitle(tableMeta.getTitle());
        em.setEntityType(EntityType.Table);
        if (tableMeta.getVersionControl() != null) {
            em.setVersionControl(tableMeta.getVersionControl());
        }
        if (tableMeta.getCacheType() != null) {
            em.setCacheType(EntityCacheType.fromStringIgnoreCase(tableMeta.getCacheType()));
        }
        if (columnList == null || columnList.isEmpty()) {
            throw new RuntimeException("column list is empty!");
        } else {
            HashMap<String, FieldMeta> columnMap = getColumnFieldMetas(columnList);
            em.setFieldMetas(columnMap.values());
            em.setId(getPrimaryKey(columnMap));
        }

        if (viewList != null && !viewList.isEmpty()) {
            HashMap<String, ViewMeta> viewMap = getViewMetas(viewList);
            em.setViewMetas(viewMap.values());
        }

        if (checkList != null && !checkList.isEmpty()) {
            em.setTableChecks(getTableCheckMetas(checkList));
        }

        if (foreignList != null && !foreignList.isEmpty()) {
            em.setTableForeigns(getTableForeignMetas(foreignList));
        }
        return em;
    }

    /**
     * 兼容旧入口：接收 platform_dev_* 行 Map 列表，转为强类型后委托给主方法。
     * <p>MetaSourceLoader 等仍在使用此入口。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EntityMeta getEntityMetaByTable(Map tmap, List columnList, List viewList, List checkList, List foreignList) {
        List<ColumnMeta> columns = new ArrayList<>();
        if (columnList != null) {
            for (Object o : columnList) {
                columns.add(new ColumnMeta((Map<String, Object>) o));
            }
        }
        List<TableView> views = new ArrayList<>();
        if (viewList != null) {
            for (Object o : viewList) {
                views.add(new TableView((Map<String, Object>) o));
            }
        }
        List<TableCheck> checks = new ArrayList<>();
        if (checkList != null) {
            for (Object o : checkList) {
                checks.add(new TableCheck((Map<String, Object>) o));
            }
        }
        List<TableForeign> foreigns = new ArrayList<>();
        if (foreignList != null) {
            for (Object o : foreignList) {
                foreigns.add(new TableForeign((Map<String, Object>) o));
            }
        }
        return getEntityMetaByTable(new TableMeta(tmap), columns, views, checks, foreigns);
    }

    public static EntityMeta getEntityMetaByView(TableView view) {
        EntityMeta em = new EntityMeta();
        // view 行与 table 行部分字段重叠，构造 TableMeta 时复制这些重叠字段
        TableMeta tableMeta = new TableMeta();
        tableMeta.setAppId(view.getAppId());
        tableMeta.setConnectId(view.getConnectId());
        tableMeta.setEntityName(view.getEntityName());
        tableMeta.setTitle(view.getTitle());
        tableMeta.setId(view.getId());
        tableMeta.setDelStatus(view.getDelStatus());
        String viewName = view.getViewName();
        String subjectEntityName = view.getEntityName();
        if (StringUtils.hasText(subjectEntityName)) {
            EntityMeta subjectEntityMeta = MetaManager.singleInstance().getByEntityName(subjectEntityName);
            if (subjectEntityMeta != null && subjectEntityMeta.getTableMeta() != null) {
                tableMeta.setDbType(subjectEntityMeta.getTableMeta().getDbType());
            }
        }
        tableMeta.setTableName(viewName);
        em.setTableMeta(tableMeta);
        em.setEntityName(viewName);
        em.setEntityTitle(view.getTitle());
        em.setEntityType(EntityType.View);
        ViewMeta viewMeta = buildViewMeta(view);
        if (viewMeta != null) {
            em.setViewMetas(Collections.singletonList(viewMeta));
            ViewManager.singleInstance().addViewMeta(viewMeta.getViewName(), viewMeta);
        }
        String columnDataStr = view.getViewColumn();
        if (StringUtils.hasText(columnDataStr)) {
            List<Map<String, Object>> list = new ArrayList<>();
            JSONArray columnData = JSONArray.parse(columnDataStr);
            columnData.forEach(x -> {
                Map<String, Object> m = JSON.parseObject(x.toString(), Map.class);
                list.add(m);
            });
            HashMap<String, FieldMeta> columnMap = getColumnFieldMetasFromMap(list);
            em.setFieldMetas(columnMap.values());
            em.setId(getPrimaryKey(columnMap));
        }
        return em;
    }

    /**
     * 兼容旧入口：接收 view 行 Map，转为 {@link TableView} 后委托给主方法。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EntityMeta getEntityMetaByView(Map<String, Object> map) {
        return getEntityMetaByView(new TableView(map));
    }

    private static ViewMeta buildViewMeta(TableView view) {
        String viewName = view.getViewName();
        if (Strings.isBlank(viewName)) {
            return null;
        }
        return new ViewMeta(viewName, view.getViewType(), view.getViewConstruct(), view.getViewColumn(), view.getEntityName());
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

    public static HashMap<String, ViewMeta> getViewMetas(List<TableView> viewList) {
        HashMap<String, ViewMeta> map = new HashMap<>();
        if (viewList != null && !viewList.isEmpty()) {
            for (TableView view : viewList) {
                try {
                    String viewName = view.getViewName();
                    if (Strings.isNotBlank(viewName) && !map.containsKey(viewName)) {
                        ViewMeta vm = new ViewMeta(viewName, view.getViewType(), view.getViewConstruct(), view.getViewColumn(), view.getEntityName());
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

    /**
     * 兼容入口：接收 view 行 Map 列表，转为 {@link TableView} 后委托给强类型主方法。
     * <p>因类型擦除，List&lt;HashMap&gt; 与 List&lt;TableView&gt; 无法重载，故独立命名。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static HashMap<String, ViewMeta> getViewMetasFromMap(List<HashMap> viewList) {
        List<TableView> list = new ArrayList<>();
        if (viewList != null) {
            for (Object o : viewList) {
                list.add(new TableView((Map<String, Object>) o));
            }
        }
        return getViewMetas(list);
    }

    public static HashMap<String, FieldMeta> getColumnFieldMetas(List<ColumnMeta> columnList) {
        HashMap<String, FieldMeta> map = new HashMap<>();
        for (ColumnMeta cm : columnList) {
            String fieldName = cm.getFieldName();
            if (Strings.isNotBlank(fieldName) && !map.containsKey(fieldName)) {
                FieldMeta cfm = new FieldMeta(cm);
                String dataType = cm.getDataType();
                // 派生：文本类型清空默认值；按 dataType 推导 Java 字段类型
                if (MysqlDataTypeEnum.getTexts().contains(dataType)) {
                    cm.setDefaultValue(null);
                }
                cfm.setFieldType(MysqlToJavaEnum.getJava(dataType));
                map.put(fieldName, cfm);
            }
        }
        return map;
    }

    /**
     * 兼容入口：接收 platform_dev_column 行 Map 列表，转为 {@link ColumnMeta} 后委托给强类型主方法。
     * <p>view_column JSON 解析路径等仍在使用此入口。因类型擦除，List&lt;Map&gt; 与 List&lt;ColumnMeta&gt; 无法重载，故独立命名。</p>
     */
    public static HashMap<String, FieldMeta> getColumnFieldMetasFromMap(List<Map<String, Object>> columnList) {
        List<ColumnMeta> list = new ArrayList<>();
        if (columnList != null) {
            for (Map<String, Object> map : columnList) {
                list.add(new ColumnMeta(map));
            }
        }
        return getColumnFieldMetas(list);
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

    public static List<TableForeign> getTableForeignMetas(List<TableForeign> foreignList) {
        List<TableForeign> foreigns = new ArrayList<>();
        if (foreignList != null && !foreignList.isEmpty()) {
            foreigns.addAll(foreignList);
        }
        return foreigns;
    }

    /**
     * 兼容入口：接收外键行 Map 列表，转为 {@link TableForeign} 后委托给强类型主方法。
     * <p>因类型擦除，List&lt;HashMap&gt; 与 List&lt;TableForeign&gt; 无法重载，故独立命名。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<TableForeign> getTableForeignMetasFromMap(List<HashMap> foreignList) {
        List<TableForeign> list = new ArrayList<>();
        if (foreignList != null) {
            for (Object o : foreignList) {
                list.add(new TableForeign((Map<String, Object>) o));
            }
        }
        return getTableForeignMetas(list);
    }

    public static List<TableCheck> getTableCheckMetas(List<TableCheck> checkList) {
        List<TableCheck> checks = new ArrayList<>();
        if (checkList != null && !checkList.isEmpty()) {
            checks.addAll(checkList);
        }
        return checks;
    }

    /**
     * 兼容入口：接收检查行 Map 列表，转为 {@link TableCheck} 后委托给强类型主方法。
     * <p>因类型擦除，List&lt;HashMap&gt; 与 List&lt;TableCheck&gt; 无法重载，故独立命名。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<TableCheck> getTableCheckMetasFromMap(List<HashMap> checkList) {
        List<TableCheck> list = new ArrayList<>();
        if (checkList != null) {
            for (Object o : checkList) {
                list.add(new TableCheck((Map<String, Object>) o));
            }
        }
        return getTableCheckMetas(list);
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
        if (beforeFieldHashMap.isEmpty()) {
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
