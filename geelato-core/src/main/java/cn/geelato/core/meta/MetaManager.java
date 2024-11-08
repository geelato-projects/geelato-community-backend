package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.constants.ResourcesFiles;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.model.entity.EntityLiteMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.schema.SchemaForeign;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.FastJsonUtils;
import cn.geelato.utils.ClassScanner;
import cn.geelato.utils.MapUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.*;

/**
 * @author geemeta
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class MetaManager extends AbstractManager {

    private Dao dao;
    private static MetaManager instance;
    private final HashMap<String, EntityMeta> entityMetadataMap = new HashMap<>();
    private final List<EntityLiteMeta> entityLiteMetaList = new ArrayList<>();
    private final HashMap<String, EntityMeta> tableNameMetadataMap = new HashMap<>();
    private static final HashMap<String, String> entityFieldNameTitleMap = new HashMap<>();
    private final Map<String, FieldMeta> commonFieldMetas = new HashMap<>();

    public static MetaManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new MetaManager();
        }
        lock.unlock();
        return instance;
    }

    private MetaManager() {
        log.info("MetaManager Instancing...");
        // 解析内置的类

        parseOne(ColumnMeta.class);
        parseOne(TableMeta.class);
//        // 内置默认的公共字段, todo : 似乎无用
//        addCommonFieldMeta("name", "name", "名称");
//        addCommonFieldMeta("type", "type", "类型");
//        addCommonFieldMeta("creator", "creator", "创建者");
//        addCommonFieldMeta("updater", "updater", "更新者");
//        addCommonFieldMeta("create_at", "createAt", "创建日期");
//        addCommonFieldMeta("update_at", "updateAt", "更新日期");
//        addCommonFieldMeta("description", "description", "描述", 1024);
//        addCommonFieldMeta("id", "id", "序号");
//        addCommonFieldMeta("title", "title", "标题");
//        addCommonFieldMeta("password", "password", "密码");
//        addCommonFieldMeta("login_name", "loginName", "登录名");
    }

    public void parseDBMeta(Dao dao) {
        this.dao = dao;
        log.info("parse meta data in database...");
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(MetaDaoSql.SQL_TABLE_LIST);
        for (Map<String, Object> map : tableList) {
            List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", map.get("id")));
            List<Map<String, Object>> viewList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and entity_name='%s'", map.get("entity_name")));
            parseTableEntity(map, columnList, viewList);
            parseViewEntity(viewList);
        }
    }

    /**
     * 根据需求刷新模型和视图
     *
     * @param params appId,connectId,tableId,entityName
     */
    public void parseDBMeta(Dao dao, Map<String, String> params) {
        this.dao = dao;
        log.info("parse meta data in database...");
        String sql = MetaDaoSql.SQL_TABLE_LIST;
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (Strings.isNotBlank(entry.getValue())) {
                    sql = String.format("%s and find_in_set(%s, '%s')", sql, entry.getKey(), entry.getValue());
                }
            }
        }
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(sql);
        for (Map<String, Object> map : tableList) {
            List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", map.get("id")));
            List<Map<String, Object>> viewList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and entity_name='%s'", map.get("entity_name")));
            parseTableEntity(map, columnList, viewList);
            parseViewEntity(viewList);
        }
    }

    public void refreshDBMeta(String entityName) {
        log.info("refresh meta...{}", entityName);
        refreshTableMeta(entityName);
        refreshViewMeta(entityName);

    }

    private void refreshViewMeta(String entityName) {
        String viewListSql = MetaDaoSql.SQL_VIEW_LIST_BY_TABLE;
        if (Strings.isNotEmpty(entityName)) {
            viewListSql = String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and view_name='%s'", entityName);
        }
        List<Map<String, Object>> viewList = dao.getJdbcTemplate().queryForList(viewListSql);
        for (Map<String, Object> map : viewList) {
            removeOne(entityName);
            parseViewEntity(map);
        }
    }

    private void refreshTableMeta(String entityName) {
        String tableListSql = MetaDaoSql.SQL_TABLE_LIST;
        if (Strings.isNotEmpty(entityName)) {
            tableListSql = String.format(MetaDaoSql.SQL_TABLE_LIST + " and entity_name='%s'", entityName);
        }
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(tableListSql);
        for (Map<String, Object> map : tableList) {
            List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", map.get("id")));
            removeOne(entityName);
            parseOne(map, columnList);
        }
    }

    public List<SchemaIndex> queryIndexes(String tableName) {
        List<SchemaIndex> indexList = new ArrayList<>();
        if (Strings.isEmpty(tableName)) {
            return indexList;
        }
        List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_INDEXES_NO_PRIMARY, tableName));
        indexList = SchemaIndex.buildData(mapList);
        return indexList;
    }

    public List<SchemaIndex> queryIndexes(String tableName, String columnName, Boolean isUnique, Boolean isPrimary) {
        List<SchemaIndex> indexList = new ArrayList<>();
        if (!Strings.isEmpty(tableName)) {
            List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_INDEXES_NO_PRIMARY, tableName));
            indexList = SchemaIndex.buildData(mapList);
        }
        return indexList;
    }

    public List<SchemaForeign> queryForeignKeys(String tableName) {
        List<SchemaForeign> keyList = new ArrayList<>();
        if (Strings.isEmpty(tableName)) {
            return keyList;
        }
        List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_FOREIGN_KEY, tableName));
        keyList = SchemaForeign.buildTableForeignKeys(mapList);
        return keyList;
    }

    /**
     * 添加公共字段
     *
     * @param columnName 列名
     * @param fieldName  字段名
     * @param title      字段显示名，如中文名
     */
//    public void addCommonFieldMeta(String columnName, String fieldName, String title) {
//        FieldMeta fieldMeta = new FieldMeta(columnName, fieldName, title);
//        commonFieldMetas.put(fieldName, fieldMeta);
//    }

    /**
     * 添加公共字段
     *
     * @param columnName    列名
     * @param fieldName     字段名
     * @param title         字段显示名，如中文名
     * @param charMaxLength 字符长
     */
//    public void addCommonFieldMeta(String columnName, String fieldName, String title, int charMaxLength) {
//        FieldMeta fieldMeta = new FieldMeta(columnName, fieldName, title);
//        fieldMeta.getColumn().setCharMaxLength(charMaxLength);
//        commonFieldMetas.put(fieldName, fieldMeta);
//    }

    /**
     *
     */
//    public FieldMeta getCommonFieldMeta(String columnName) {
//        return commonFieldMetas.get(columnName);
//    }


    public EntityMeta get(Class clazz) {
        String entityName = MetaReflex.getEntityName(clazz);
        if (entityMetadataMap.containsKey(entityName)) {
            return entityMetadataMap.get(entityName);
        } else {
            Iterator<String> it = entityMetadataMap.keySet().iterator();
            log.warn("Key({}) not found in entityMetadataMap by class", clazz.getName());
            while (it.hasNext()) {
                log.warn(it.next());
            }
            return null;
        }
    }

    public Map<String, Object> newDefaultEntity(String entityName) {
        return newDefaultEntity(getByEntityName(entityName));
    }



    public Map<String, Object> newDefaultEntity(Class clazz) {
        return newDefaultEntity(get(clazz));
    }

    /**
     * 基于元数据，创建默认实体（Map），并以各字段的默认值填充
     *
     * @param em 实体元数据
     * @return 返回填充后的map
     */
    public Map<String, Object> newDefaultEntity(EntityMeta em) {
        HashMap<String, Object> map = new HashMap<>(em.getFieldMetas().size());
        for (FieldMeta fm : em.getFieldMetas()) {
            ColumnMeta cm = fm.getColumn();
            if (cm.getEnableStatus() == EnableStatusEnum.ENABLED.getCode() && cm.getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                if (boolean.class.equals(fm.getFieldType()) || Boolean.class.equals(fm.getFieldType())) {
                    map.put(fm.getFieldName(), Strings.isNotBlank(cm.getDefaultValue()) ? Integer.parseInt(cm.getDefaultValue()) : null);
                } else {
                    map.put(fm.getFieldName(), cm.getDefaultValue());
                }
            }
        }
        return map;
    }


    /**
     * @param entityName 实体名称，若是java元数据，则entityName为长类名（包名+类名）
     * @return 实体元数据
     */
    public EntityMeta getByEntityName(String entityName) {
        if (entityMetadataMap.containsKey(entityName)) {
            return entityMetadataMap.get(entityName);
        } else {
            Iterator<String> it = entityMetadataMap.keySet().iterator();
            log.warn("Key({}) not found in entityMetadataMap by entityName", entityName);
            while (it.hasNext()) {
                log.warn(it.next());
            }
            return null;
        }
    }

    public EntityMeta getByEntityName(String entityName, boolean cache) {
        if (cache) {
            return getByEntityName(entityName);
        }
        refreshDBMeta(entityName);
        return getByEntityName(entityName);
    }

    public boolean containsEntity(String entityName) {
        return entityMetadataMap.containsKey(entityName);
    }

    public EntityMeta get(String tableName) {
        if (tableNameMetadataMap.containsKey(tableName)) {
            return tableNameMetadataMap.get(tableName);
        } else {
            Iterator<String> it = tableNameMetadataMap.keySet().iterator();
            log.warn("Key({}) not found in tableNameMetadataMap by tableName", tableName);
            while (it.hasNext()) {
                log.warn(it.next());
            }
            return null;
        }
    }

    public Collection<EntityMeta> getAll() {
        return entityMetadataMap.values();
    }

    public Collection<String> getAllEntityNames() {
        return entityMetadataMap.keySet();
    }

    public List<EntityLiteMeta> getAllEntityLiteMetas() {
        if (!entityLiteMetaList.isEmpty()) {
            List<EntityLiteMeta> liteMetas = new ArrayList<>();
            Set<String> entityName = new HashSet<>();
            for (EntityLiteMeta liteMeta : entityLiteMetaList) {
                if (entityName.contains(liteMeta.getEntityName())) {
                    liteMetas.add(liteMeta);
                }
                entityName.add(liteMeta.getEntityName());
            }
            entityLiteMetaList.removeAll(liteMetas);
        }

        return entityLiteMetaList;
    }


    /**
     * 检索批定包名中包含所有的包javax.persistence.Entity的类，并进行解析
     */
    private void scanAndParse(String parkeName) {
        // TODO 启动的时候扫描实体类，这里做个开关，如果开启，就默认将实体类更新至数据库。
        log.debug("开始从包{}中扫描到包含注解{}的实体......", parkeName, Entity.class);
        List<Class<?>> classes = ClassScanner.scan(parkeName, true, Entity.class);
        for (Class<?> clazz : classes) {
            parseOne(clazz);
        }
    }

    /**
     * @param packageName            扫描的包名
     * @param isUpdateMetadataFormDb 是否同时从数据库的元数据表中更新元数据信息，如字段长度
     */
    public void scanAndParse(String packageName, boolean isUpdateMetadataFormDb) {
        scanAndParse(packageName);
        if (isUpdateMetadataFormDb) {
            //todo 解析实体类，写入到数据库
            updateMetadataFromDbAfterParse(null);
        }
    }

    /**
     * 从数据库的元数据表中更新元数据信息，如字段长度
     * 注：需在scanAndParse之后执行才有效
     *
     * @param columns 待更新的列
     */
    public void updateMetadataFromDbAfterParse(List<HashMap<?, ?>> columns) {
        for (HashMap<?, ?> map : columns) {
            String TABLE_NAME = map.get("TABLE_NAME").toString();
            EntityMeta entityMapping = null;
            for (EntityMeta obj : entityMetadataMap.values()) {
                if (obj.getTableName().equalsIgnoreCase(TABLE_NAME)) {
                    entityMapping = obj;
                    break;
                }
            }
            if (entityMapping == null) {
                continue;
            }
            String COLUMN_NAME = map.get("COLUMN_NAME").toString();
            String COLUMN_COMMENT = MapUtils.getOrDefaultString(map, "COLUMN_COMMENT", "");
            String ORDINAL_POSITION = MapUtils.getOrDefaultString(map, "ORDINAL_POSITION", "");
            String COLUMN_DEFAULT = MapUtils.getOrDefaultString(map, "COLUMN_DEFAULT", "");
            String IS_NULLABLE = MapUtils.getOrDefaultString(map, "IS_NULLABLE", "NO");
            String DATA_TYPE = MapUtils.getOrDefaultString(map, "DATA_TYPE", "varchar");
            String CHARACTER_MAXIMUM_LENGTH = MapUtils.getOrDefaultString(map, "CHARACTER_MAXIMUM_LENGTH", "20");
            String CHARACTER_OCTET_LENGTH = MapUtils.getOrDefaultString(map, "CHARACTER_OCTET_LENGTH", "24");
            String NUMERIC_PRECISION = MapUtils.getOrDefaultString(map, "NUMERIC_PRECISION", "8");
            String NUMERIC_SCALE = MapUtils.getOrDefaultString(map, "NUMERIC_SCALE", "2");
            String DATETIME_PRECISION = MapUtils.getOrDefaultString(map, "DATETIME_PRECISION", "");
            String COLUMN_TYPE = MapUtils.getOrDefaultString(map, "COLUMN_TYPE", "");
            String COLUMN_KEY = MapUtils.getOrDefaultString(map, "COLUMN_KEY", "");
            String EXTRA = MapUtils.getOrDefaultString(map, "EXTRA", "");

            FieldMeta fm = entityMapping.getFieldMetaByColumn(COLUMN_NAME);
            if (fm != null) {
                fm.getColumn().setCharMaxLength(Integer.parseInt(CHARACTER_MAXIMUM_LENGTH));
                fm.getColumn().setNullable(!"NO".equalsIgnoreCase(IS_NULLABLE));
                fm.getColumn().setExtra(EXTRA);
                fm.getColumn().setNumericPrecision(Integer.parseInt(NUMERIC_PRECISION));
                fm.getColumn().setNumericScale(Integer.parseInt(NUMERIC_SCALE));
                fm.getColumn().setComment(COLUMN_COMMENT);
                if (Strings.isEmpty(fm.getColumn().getTitle())) {
                    fm.getColumn().setTitle(COLUMN_COMMENT);
                }
                fm.getColumn().setOrdinalPosition(Integer.parseInt(ORDINAL_POSITION));
            }
        }
    }

    /**
     * 解析一个类，并将其加入到实体元数据缓存中
     *
     * @param clazz 待解析的类
     */
    public void parseOne(Class clazz) {
        log.info("parse meta from class :{}", clazz.getName());
        String entityName = MetaReflex.getEntityName(clazz);
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMeta(clazz);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Class));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            if (log.isDebugEnabled()) {
                log.debug("success in parsing class:{}", clazz.getName());
                for (FieldMeta fm : entityMeta.getFieldMetas()) {
                    if (!entityFieldNameTitleMap.containsKey(fm.getFieldName())) {
                        entityFieldNameTitleMap.put(fm.getFieldName(), fm.getTitle());
                    }
                    if (!entityFieldNameTitleMap.containsKey(fm.getColumnName())) {
                        entityFieldNameTitleMap.put(fm.getColumnName(), fm.getTitle());
                    }
                }
            }
        }
    }


    public void parseOne(Map<String, Object> map, List<Map<String, Object>> columnList) {
        parseTableEntity(map, columnList, null);
    }

    public void parseTableEntity(Map<String, Object> map, List<Map<String, Object>> columnList, List<Map<String, Object>> viewList) {
        parseTableEntity(map, columnList, viewList, null);
    }

    public void parseTableEntity(Map<String, Object> map, List<Map<String, Object>> columnList, List<Map<String, Object>> viewList, List<Map<String, Object>> foreignList) {
        String entityName = map.get("entity_name").toString();
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMetaByTable(map, columnList, viewList, foreignList);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
        }
    }

    public void parseViewEntity(List<Map<String, Object>> viewList) {
        for (Map<String, Object> view : viewList) {
            parseViewEntity(view);
        }
    }

    public void parseViewEntity(Map<String, Object> view) {
        String viewType = view.get("view_type").toString();
        if (viewType.equals("custom")) {
            String entityName = view.get("view_name").toString();
            if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
                EntityMeta entityMeta = MetaReflex.getEntityMetaByView(view);
                entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
                removeLiteMeta(entityMeta.getEntityName());
                entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.View));
                tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            }
        }
    }

    public void removeOne(String entityName) {
        if (entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = entityMetadataMap.get(entityName);
            tableNameMetadataMap.remove(entityMeta.getTableName());
            entityMetadataMap.remove(entityName);
            removeLiteMeta(entityName);
        }
    }

    public void removeLiteMeta(String entityName) {
        List<EntityLiteMeta> removeList = new ArrayList<>();
        if (!entityLiteMetaList.isEmpty()) {
            for (EntityLiteMeta liteMeta : entityLiteMetaList) {
                if (liteMeta.getEntityName().equals(entityName)) {
                    removeList.add(liteMeta);
                }
            }
            if (!removeList.isEmpty()) {
                entityLiteMetaList.removeAll(removeList);
            }
        }
    }

    public List<ColumnMeta> getDefaultColumn() {
        List<ColumnMeta> defaultColumnMetaList = new ArrayList<ColumnMeta>();

        try {
            String jsonStr = FastJsonUtils.readJsonFile(ResourcesFiles.COLUMN_DEFAULT_JSON);
            List<ColumnMeta> columnMetaList = JSON.parseArray(jsonStr, ColumnMeta.class);
            if (columnMetaList != null && !columnMetaList.isEmpty()) {
                for (ColumnMeta meta : columnMetaList) {
                    meta.afterSet();
                    defaultColumnMetaList.add(meta);
                }
            }
        } catch (IOException e) {
            defaultColumnMetaList = new ArrayList<>();
        }

        return defaultColumnMetaList;
    }

    public List<ColumnSelectType> getColumnSelectType() {
        List<ColumnSelectType> columnSelectTypes = new ArrayList<ColumnSelectType>();

        try {
            String jsonStr = FastJsonUtils.readJsonFile(ResourcesFiles.COLUMN_SELECT_TYPE_JSON);
            List<ColumnSelectType> selectTypeList = JSON.parseArray(jsonStr, ColumnSelectType.class);
            if (selectTypeList != null && !selectTypeList.isEmpty()) {
                for (ColumnSelectType selectType : selectTypeList) {
                    if (Strings.isBlank(selectType.getLabel()) || Strings.isBlank(selectType.getValue()) || Strings.isBlank(selectType.getMysql())) {
                        continue;
                    }
                    selectType.setValue(selectType.getValue().toUpperCase(Locale.ENGLISH));
                    selectType.setMysql(selectType.getMysql().toUpperCase(Locale.ENGLISH));
                    selectType.setJava(MysqlToJavaEnum.getJava(selectType.getMysql()));
                    selectType.setRadius(DataTypeRadiusEnum.getRadius(selectType.getMysql()));
                    // 固定长度时，默认长度为null、0时，默认长度为数据类型的最大值
                    if (selectType.getFixed() && (selectType.getExtent() == null || selectType.getExtent() == 0)) {
                        selectType.setExtent(selectType.getRadius().getMax());
                    }
                    columnSelectTypes.add(selectType);
                }
                columnSelectTypes.sort(new Comparator<ColumnSelectType>() {
                    @Override
                    public int compare(ColumnSelectType o1, ColumnSelectType o2) {
                        return o1.getSeqNo().intValue() - o2.getSeqNo().intValue();
                    }
                });
            }
        } catch (IOException e) {
            columnSelectTypes = new ArrayList<>();
        }

        return columnSelectTypes;
    }
}
