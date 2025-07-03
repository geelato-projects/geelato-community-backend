package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.constants.ResourcesFiles;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.entity.EntityLiteMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.MapUtils;
import cn.geelato.utils.ClassScanner;
import cn.geelato.utils.FastJsonUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author geemeta
 */
@Slf4j
@SuppressWarnings("rawtypes")
public class MetaManager extends AbstractManager {

    /**
     * 实体字段，key:字段标识或名称，columnName,fieldName，value：字段标题title
     */
    private static final HashMap<String, String> entityFieldNameTitleMap = new HashMap<>();
    private static MetaManager instance;
    /**
     * 实体类名称和实体类对象的映射关系,key:entityName，value为实体类对象
     */
    private final HashMap<String, EntityMeta> entityMetadataMap = new HashMap<>();
    /**
     * 实体类名称和实体类对象的映射关系,key:tableName，value为实体类对象,已在数据库中创建的实体类
     */
    private final HashMap<String, EntityMeta> tableNameMetadataMap = new HashMap<>();
    /**
     * 简化实体数据集合，标识，标题，类型
     */
    private final List<EntityLiteMeta> entityLiteMetaList = new ArrayList<>();
    /**
     * 实体类
     */
    private final HashMap<String, Class> entityMetaClassMap = new HashMap<>();
    private Dao dao;

    private MetaManager() {
        log.info("MetaManager Instancing...");
        parseOne(ColumnMeta.class);
        parseOne(TableMeta.class);
    }

    public static MetaManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new MetaManager();
        }
        lock.unlock();
        return instance;
    }

    /**
     * 解析数据库元数据
     * <br>所有表，视图，字段，外键，检查
     *
     * @param dao 数据访问对象
     */
    public void parseDBMeta(Dao dao) {
        this.dao = dao;
        parseDBMeta(dao, null);
    }

    /**
     * 根据需求刷新模型和视图。
     * <br>根据传入的参数，从数据库中查询表信息、列信息和视图信息，并对这些信息进行处理以刷新模型和视图。
     *
     * @param dao    数据访问对象，用于执行数据库操作
     * @param params 包含查询参数的Map，支持的参数包括appId、connectId、tableId和entityName
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
        List<Map<String, Object>> allColumnList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE));
        List<Map<String, Object>> allViewList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE));
        List<Map<String, Object>> allCheckList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE));
        for (Map<String, Object> map : tableList) {
            String tableId = map.get("id") == null ? "" : map.get("id").toString();
            String entityName = map.get("entity_name") == null ? "" : map.get("entity_name").toString();
            String connectId = map.get("connect_id") == null ? "" : map.get("connect_id").toString();
            if (StringUtils.isAnyBlank(tableId, entityName, connectId)) {
                continue;
            }
            List<Map<String, Object>> columnList = allColumnList.stream().filter(
                    x -> x.get("table_id") != null && x.get("table_id").toString().equals(tableId)
            ).collect(Collectors.toList());
            List<Map<String, Object>> viewList = allViewList.stream().filter(
                    x -> x.get("entity_name") != null && x.get("entity_name").toString().equals(entityName)
                            && x.get("connect_id") != null && x.get("connect_id").toString().equals(connectId)
            ).collect(Collectors.toList());
            List<Map<String, Object>> checkList = allCheckList.stream().filter(
                    x -> x.get("table_id") != null && x.get("table_id").toString().equals(tableId)
            ).collect(Collectors.toList());
            parseTableEntity(map, columnList, viewList, checkList, null);
            parseViewEntity(viewList);
        }
    }

    /**
     * 刷新数据库元数据
     *
     * @param entityName 实体名称
     */
    public void refreshDBMeta(String entityName) {
        log.info("refresh meta...{}", entityName);
        refreshTableMeta(entityName);
        refreshViewMeta(entityName);

    }

    /**
     * 刷新视图元数据,删除原来的，重新解析
     *
     * @param viewName 视图名称
     */
    private void refreshViewMeta(String viewName) {
        String viewListSql = MetaDaoSql.SQL_VIEW_LIST_BY_TABLE;
        if (Strings.isNotEmpty(viewName)) {
            viewListSql = String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and view_name='%s'", viewName);
        }
        List<Map<String, Object>> viewList = dao.getJdbcTemplate().queryForList(viewListSql);
        for (Map<String, Object> map : viewList) {
            removeOne(viewName);
            parseViewEntity(map);
        }
    }

    /**
     * 刷新表元数据
     *
     * @param entityName 实体名称
     */
    private void refreshTableMeta(String entityName) {
        String tableListSql = MetaDaoSql.SQL_TABLE_LIST;
        if (Strings.isNotEmpty(entityName)) {
            tableListSql = String.format(MetaDaoSql.SQL_TABLE_LIST + " and entity_name='%s'", entityName);
        }
        List<Map<String, Object>> tableList = dao.getJdbcTemplate().queryForList(tableListSql);
        for (Map<String, Object> map : tableList) {
            List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_COLUMN_LIST_BY_TABLE + " and table_id='%s'", map.get("id")));
            List<Map<String, Object>> viewList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_VIEW_LIST_BY_TABLE + " and entity_name='%s' and connect_id='%s'", map.get("entity_name"), map.get("connect_id")));
            List<Map<String, Object>> checkList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE + " and table_id='%s'", map.get("id")));
            removeOne(entityName);
            parseTableEntity(map, columnList, viewList, checkList, null);
        }
    }

    /**
     * 根据类名获取实体元数据
     *
     * @param clazz 类对象
     * @return 实体元数据对象，如果不存在则返回null
     */
    public EntityMeta get(Class clazz) {
        String entityName = MetaReflex.getEntityName(clazz);
        if (entityMetadataMap.containsKey(entityName)) {
            return entityMetadataMap.get(entityName);
        } else {
            log.warn("Key({}) not found in entityMetadataMap by class", clazz.getName());
            return null;
        }
    }

    /**
     * 根据实体名称获取实体元数据。
     * 通过传入的实体名称（entityName）在entityMetadataMap中查找对应的实体元数据，并返回。
     * 如果未找到对应的实体元数据，则记录警告日志并返回null。
     *
     * @param entityName 实体名称。如果是Java元数据，则entityName应为长类名（包名+类名）。
     * @return 返回找到的实体元数据对象，如果未找到则返回null。
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

    public Map<String, Object> newDefaultEntityMap(String entityName) {
        return newDefaultEntityMap(getByEntityName(entityName));
    }


    public Map<String, Object> newDefaultEntityMap(Class clazz) {
        return newDefaultEntityMap(get(clazz));
    }

    /**
     * 基于元数据，创建默认实体（Map），并以各字段的默认值填充
     *
     * @param em 实体元数据
     * @return 返回填充后的map
     */
    public Map<String, Object> newDefaultEntityMap(EntityMeta em) {
        HashMap<String, Object> map = new HashMap<>(em.getFieldMetas().size());
        for (FieldMeta fm : em.getFieldMetas()) {
            ColumnMeta cm = fm.getColumnMeta();
            if (cm.getEnableStatus() == ColumnDefault.ENABLE_STATUS_VALUE && cm.getDelStatus() == ColumnDefault.DEL_STATUS_VALUE) {
                if (boolean.class.equals(fm.getFieldType()) || Boolean.class.equals(fm.getFieldType())) {
                    map.put(fm.getFieldName(), Strings.isNotBlank(cm.getDefaultValue()) ? Integer.parseInt(cm.getDefaultValue()) : null);
                } else {
                    map.put(fm.getFieldName(), cm.getDefaultValue());
                }
            }
        }
        return map;
    }

    public boolean containsEntity(String entityName) {
        return entityMetadataMap.containsKey(entityName);
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
     * 扫描并解析指定包名下的所有类。
     *
     * @param packageName            要扫描的包名
     * @param isUpdateMetadataFormDb 是否在解析类之后，从数据库的元数据表中更新类的元数据信息，如字段长度等
     */
    public void scanAndParse(String packageName, boolean isUpdateMetadataFormDb) {
        scanAndParse(packageName);
        if (isUpdateMetadataFormDb) {
            // todo 解析实体类，写入到数据库
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
                fm.getColumnMeta().setCharMaxLength(Integer.parseInt(CHARACTER_MAXIMUM_LENGTH));
                fm.getColumnMeta().setNullable(!"NO".equalsIgnoreCase(IS_NULLABLE));
                fm.getColumnMeta().setExtra(EXTRA);
                fm.getColumnMeta().setNumericPrecision(Integer.parseInt(NUMERIC_PRECISION));
                fm.getColumnMeta().setNumericScale(Integer.parseInt(NUMERIC_SCALE));
                fm.getColumnMeta().setComment(COLUMN_COMMENT);
                if (Strings.isEmpty(fm.getColumnMeta().getTitle())) {
                    fm.getColumnMeta().setTitle(COLUMN_COMMENT);
                }
                fm.getColumnMeta().setOrdinalPosition(Integer.parseInt(ORDINAL_POSITION));
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
            entityMetaClassMap.put(entityMeta.getTableName(), clazz);
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

    public void parseOneOther(EntityMeta entityMeta) {
        List checkList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_CHECK_LIST_BY_TABLE + " and table_id='%s'", entityMeta.getTableMeta().getId()));
        if (!checkList.isEmpty()) {
            entityMeta.setTableChecks(MetaReflex.getTableCheckMetas(checkList));
        }
    }


    public void parseOne(Map<String, Object> map, List<Map<String, Object>> columnList) {
        parseTableEntity(map, columnList, null);
    }

    public void parseTableEntity(Map<String, Object> map, List<Map<String, Object>> columnList, List<Map<String, Object>> viewList) {
        parseTableEntity(map, columnList, viewList, null, null);
    }

    public void parseTableEntity(Map<String, Object> map, List<Map<String, Object>> columnList, List<Map<String, Object>> viewList, List<Map<String, Object>> checkList, List<Map<String, Object>> foreignList) {
        String entityName = map.get("entity_name") == null ? null : map.get("entity_name").toString();
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMetaByTable(map, columnList, viewList, checkList, foreignList);
            // EntityType = class
            if (entityMetaClassMap.containsKey(entityName)) {
                entityMeta.setClassType(entityMetaClassMap.get(entityName));
                entityMeta.setEntityType(EntityType.Class);
            }
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
        } else if (entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = entityMetadataMap.get(entityName);
            if (entityMeta != null && entityMeta.getTableMeta() != null) {
                entityMeta.setTableMeta(MetaReflex.getTableMeta(map));
            }
        }
    }

    public void parseViewEntity(List<Map<String, Object>> viewList) {
        for (Map<String, Object> view : viewList) {
            parseViewEntity(view);
        }
    }

    public void parseViewEntity(Map<String, Object> view) {
        String entityName = view.get("view_name").toString();
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMetaByView(view);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.View));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
        }
    }

    /**
     * 移除指定实体的元数据
     *
     * @param entityName 要移除的实体名称或视图名称
     */
    public void removeOne(String entityName) {
        if (entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = entityMetadataMap.get(entityName);
            tableNameMetadataMap.remove(entityMeta.getTableName());
            entityMetadataMap.remove(entityName);
            removeLiteMeta(entityName);
        }
    }

    /**
     * 移除轻量级实体元数据
     *
     * @param entityName 要移除的实体名称
     */
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

    /**
     * 获取表升级列信息列表
     *
     * @return 包含列信息的Map，键为列名，值为列信息对象
     * @throws IOException 当读取JSON文件时发生IO异常
     */
    public Map<String, ColumnMeta> getTableUpgradeList() {
        Map<String, ColumnMeta> columnMetaMap = new HashMap<>();
        try {
            String jsonStr = FastJsonUtils.readJsonFile(ResourcesFiles.TABLE_UPGRADE_JSON);
            List<ColumnMeta> columnMetaList = JSON.parseArray(jsonStr, ColumnMeta.class);
            if (columnMetaList != null && !columnMetaList.isEmpty()) {
                for (ColumnMeta columnMeta : columnMetaList) {
                    columnMeta.afterSet();
                    columnMetaMap.put(columnMeta.getFieldName(), columnMeta);
                }
            }
        } catch (IOException e) {
            columnMetaMap = new HashMap<>();
        }

        return columnMetaMap;
    }
}
