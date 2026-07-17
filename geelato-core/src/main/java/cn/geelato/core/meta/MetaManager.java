package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaResourceProvider;
import cn.geelato.core.meta.spi.MetaStore;
import cn.geelato.core.meta.support.DefaultMetaResourceProvider;
import cn.geelato.core.meta.support.DefaultMetaStore;
import cn.geelato.lang.meta.Entity;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.entity.EntityLiteMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.MapUtils;
import cn.geelato.utils.ClassScanner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

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
    private final HashMap<String, EntityMeta> entityMetadataMapFromClass = new HashMap<>();
    private final HashMap<String, EntityMeta> entityMetadataMapFromDatabase = new HashMap<>();
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
//    private final HashMap<String, Class> entityMetaClassMap = new HashMap<>();
    private Dao dao;
    private MetaStore metaStore = new DefaultMetaStore();
    private MetaResourceProvider metaResourceProvider = new DefaultMetaResourceProvider();
    /**
     * 同名实体（Java类源 vs DB在线源）冲突时的合并策略：
     * <ul>
     *     <li>DATABASE：以在线DB定义为准（覆盖字段集），适合在线实体由设计器维护最新真值的场景</li>
     *     <li>CLASS：以Java类定义为准（兼容历史行为）</li>
     * </ul>
     * catalog=platform 的系统内置实体始终以 Java 类为准，不受此配置影响。
     */
    private ConflictStrategy conflictStrategy = ConflictStrategy.CLASS;
    /**
     * 冲突检测总开关，默认关闭。对应配置项 geelato.meta.conflict-detect.enabled。
     * <p>
     * 关闭时：parseOne/parseTableEntity/parseDBMeta 保持原有静默"先到先得"逻辑，不输出冲突告警、不改变覆盖行为，与改动前完全一致。
     * 开启时：才输出冲突 warn 日志、启用 diff 日志、按 conflictStrategy 处理同名冲突。
     * </p>
     */
    private boolean conflictDetectEnabled = false;

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
        MetaDefinitionBundle definitionBundle = metaStore.load(dao, params);
        List<Map<String, Object>> tableList = definitionBundle.getTableList();
        List<Map<String, Object>> allColumnList = definitionBundle.getColumnList();
        List<Map<String, Object>> allViewList = definitionBundle.getViewList();
        List<Map<String, Object>> allCheckList = definitionBundle.getCheckList();
        List<Map<String, Object>> allForeignList = definitionBundle.getForeignList();
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
            List<Map<String, Object>> foreignList = allForeignList.stream().filter(
                    x -> x.get("main_table") != null && x.get("main_table").toString().equals(entityName)
            ).collect(Collectors.toList());
            parseTableEntity(map, columnList, viewList, checkList, foreignList);
            parseViewEntity(viewList);
            // 冲突检测开关开启时：同名实体输出 Java类源 与 DB源 的字段级差异，便于发现"在线实体被静默忽略"问题
            if (conflictDetectEnabled && entityMetadataMapFromClass.containsKey(entityName)) {
                Map<String, Object> diffResult = compareEntitySourcesAll(entityName);
                MetaComapare.logDiffs(log, diffResult);
            }
        }
    }

    /**
     * 刷新数据库元数据
     *
     * @param entityName 实体名称
     */
    public void refreshDBMeta(String entityName) {
        EntityMeta em = getByEntityName(entityName);
        if (em != null && "platform".equalsIgnoreCase(em.getCatalog())) {
            throw new RuntimeException("实体标记为平台，无法刷新: " + entityName);
        }
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
        List<Map<String, Object>> viewList = metaStore.loadByViewName(dao, viewName).getViewList();
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
        MetaDefinitionBundle definitionBundle = metaStore.loadByEntityName(dao, entityName);
        List<Map<String, Object>> tableList = definitionBundle.getTableList();
        for (Map<String, Object> map : tableList) {
            List<Map<String, Object>> columnList = definitionBundle.getColumnList();
            List<Map<String, Object>> viewList = definitionBundle.getViewList();
            List<Map<String, Object>> checkList = definitionBundle.getCheckList();
            List<Map<String, Object>> foreignList = definitionBundle.getForeignList();
            removeOne(entityName);
            parseTableEntity(map, columnList, viewList, checkList, foreignList);
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

    /**
     * 基于元数据，创建默认实体（Map），并以各字段的默认值填充
     *
     * @param em 实体元数据
     * @return 返回填充后的map
     */
    public Map<String, Object> newDefaultEntityMap(EntityMeta em) {
        if (em == null || em.getFieldMetas() == null) {
            return new HashMap<>();
        }
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
    private void scanAndParse(String packageName) {
        log.debug("开始从包{}中扫描到包含注解{}的实体......", packageName, Entity.class);
        List<Class<?>> classes = ClassScanner.scan(packageName, true, Entity.class);
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
        }
    }

    /**
     * 解析一个类，并将其加入到实体元数据缓存中
     *
     * @param clazz 待解析的类
     */
    public void parseOne(Class clazz) {
        if (clazz == null) {
            return;
        }
        log.info("parse meta from class :{}", clazz.getName());
        String entityName = MetaReflex.getEntityName(clazz);
        EntityMeta entityMeta = MetaReflex.getEntityMeta(clazz);
        if (Strings.isNotBlank(entityMeta.getEntityName())) {
            entityMetadataMapFromClass.put(entityMeta.getEntityName(), entityMeta);
        }
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Class));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            printEntityTree(entityMeta);
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
        } else if (Strings.isNotBlank(entityName) && conflictDetectEnabled) {
            // 冲突检测开关开启时：已存在同名实体（通常是被更早扫描到的同名Java类占用），本类定义被忽略
            log.warn("实体名冲突：{} 已由 Java 类注册，当前类 {} 的定义被忽略", entityName, clazz.getName());
        }
    }

    public void parseTableEntity(Map<String, Object> map, List<Map<String, Object>> columnList, List<Map<String, Object>> viewList, List<Map<String, Object>> checkList, List<Map<String, Object>> foreignList) {
        String entityName = map.get("entity_name") == null ? null : map.get("entity_name").toString();
        EntityMeta entityMeta = null;
        if (Strings.isNotBlank(entityName)) {
            entityMeta = MetaReflex.getEntityMetaByTable(map, columnList, viewList, checkList, foreignList);
            entityMetadataMapFromDatabase.put(entityName, entityMeta);
        }
        if (Strings.isBlank(entityName)) {
            return;
        }
        // 冲突检测开关关闭时：保持原有静默"先到先得"逻辑（含历史半覆盖行为），与改动前完全一致
        if (!conflictDetectEnabled) {
            if (!entityMetadataMap.containsKey(entityName)) {
                entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
                removeLiteMeta(entityMeta.getEntityName());
                entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
                tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
                printEntityTree(entityMeta);
            } else if (entityMetadataMap.containsKey(entityName)) {
                entityMeta = entityMetadataMap.get(entityName);
                if (entityMeta != null && entityMeta.getTableMeta() != null) {
                    entityMeta.setTableMeta(MetaReflex.getTableMeta(map));
                }
            }
            return;
        }
        // 冲突检测开关开启时：同名冲突按 conflictStrategy 处理
        if (entityMetadataMapFromClass.containsKey(entityName)) {
            EntityMeta classMeta = entityMetadataMapFromClass.get(entityName);
            boolean classIsPlatform = classMeta != null && "platform".equalsIgnoreCase(classMeta.getCatalog());
            if (classIsPlatform || conflictStrategy == ConflictStrategy.CLASS) {
                // platform 系统内置实体 或 策略为 CLASS：以 Java 类为准，仅记录冲突，不覆盖
                log.warn("实体名冲突：{} 同时存在于 Java 类与在线DB定义，采用 Java 类定义（catalog={}, strategy={}）", entityName, classMeta.getCatalog(), conflictStrategy);
                return;
            }
            // 策略为 DATABASE：以在线 DB 定义整体覆盖，消除"表名走DB、字段走Java"的半覆盖不一致
            log.warn("实体名冲突：{} 同时存在于 Java 类与在线DB定义，采用在线DB定义覆盖（strategy={}）", entityName, conflictStrategy);
            removeOne(entityName);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            printEntityTree(entityMeta);
            return;
        }
        // 无冲突：正常注册在线实体
        if (!entityMetadataMap.containsKey(entityName)) {
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            printEntityTree(entityMeta);
        }
    }

    public void parseViewEntity(List<Map<String, Object>> viewList) {
        for (Map<String, Object> view : viewList) {
            parseViewEntity(view);
        }
    }

    public void parseViewEntity(Map<String, Object> view) {
        if (view == null || view.get("view_name") == null) {
            return;
        }
        String entityName = view.get("view_name").toString();
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMetaByView(view);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.View));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
            printEntityTree(entityMeta);
        }
    }

    public EntityMeta getClassSourceEntity(String entityName) {
        return entityMetadataMapFromClass.get(entityName);
    }

    public EntityMeta getDatabaseSourceEntity(String entityName) {
        return entityMetadataMapFromDatabase.get(entityName);
    }

    public Map<String, Object> compareEntitySources(String entityName) {
        return MetaComapare.compareEntitySources(this, entityName);
    }

    public Map<String, Object> compareEntitySourcesAll(String entityName) {
        return MetaComapare.compareEntitySourcesAll(this, entityName);
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

    private void printEntityTree(EntityMeta em) {
        String title = em.getEntityTitle() == null ? "" : em.getEntityTitle();
        String type = em.getEntityType() == null ? "" : em.getEntityType().name();
        String catalog = em.getCatalog() == null ? "none" : em.getCatalog();
        String head = em.getEntityName() + (Strings.isNotBlank(type) ? " [" + type + "]" : "") + (Strings.isNotBlank(catalog) ? " (catalog:" + catalog + ")" : "") + (Strings.isNotBlank(title) ? " (" + title + ")" : "");
        log.info(head);
        Collection<FieldMeta> fms = em.getFieldMetas();
        if (fms != null && !fms.isEmpty()) {
            int idx = 0;
            int size = fms.size();
            for (FieldMeta fm : fms) {
                String prefix = (idx == size - 1) ? "  └─ " : "  ├─ ";
                String javaType = fm.getFieldType() != null ? fm.getFieldType().getSimpleName() : "";
                String colType = fm.getColumnMeta() != null ? (org.apache.logging.log4j.util.Strings.isNotBlank(fm.getColumnMeta().getType()) ? fm.getColumnMeta().getType() : fm.getColumnMeta().getDataType()) : "";
                log.info("{}{} ({}) : {} ({})", prefix, fm.getFieldName(), javaType, fm.getColumnName(), colType);
                idx++;
            }
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
        return metaResourceProvider.getDefaultColumns();
    }

    public List<ColumnSelectType> getColumnSelectType() {
        return metaResourceProvider.getColumnSelectTypes();
    }

    /**
     * 获取表升级列信息列表
     *
     * @return 包含列信息的Map，键为列名，值为列信息对象
     */
    public Map<String, ColumnMeta> getTableUpgradeList() {
        return metaResourceProvider.getTableUpgradeColumns();
    }

    public MetaStore getMetaStore() {
        return metaStore;
    }

    public void setMetaStore(MetaStore metaStore) {
        if (metaStore != null) {
            this.metaStore = metaStore;
        }
    }

    public MetaResourceProvider getMetaResourceProvider() {
        return metaResourceProvider;
    }

    public void setMetaResourceProvider(MetaResourceProvider metaResourceProvider) {
        if (metaResourceProvider != null) {
            this.metaResourceProvider = metaResourceProvider;
        }
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        if (conflictStrategy != null) {
            this.conflictStrategy = conflictStrategy;
        }
    }

    public boolean isConflictDetectEnabled() {
        return conflictDetectEnabled;
    }

    public void setConflictDetectEnabled(boolean conflictDetectEnabled) {
        this.conflictDetectEnabled = conflictDetectEnabled;
    }

    /**
     * 获取同时存在于 Java 类源与 DB 在线源的实体名集合（即冲突实体名）。
     *
     * @return 冲突实体名列表，按名称排序
     */
    public List<String> getConflictingEntityNames() {
        List<String> names = new ArrayList<>();
        for (String name : entityMetadataMapFromClass.keySet()) {
            if (entityMetadataMapFromDatabase.containsKey(name)) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * 获取所有冲突实体的字段级差异明细。
     *
     * @return key=entityName，value=差异描述（compareEntitySources 结果）
     */
    public Map<String, Map<String, Object>> getAllConflicts() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String name : getConflictingEntityNames()) {
            result.put(name, compareEntitySources(name));
        }
        return result;
    }

    /**
     * 当前冲突实体数量。
     *
     * @return 冲突实体数
     */
    public int getConflictCount() {
        int count = 0;
        for (String name : entityMetadataMapFromClass.keySet()) {
            if (entityMetadataMapFromDatabase.containsKey(name)) {
                count++;
            }
        }
        return count;
    }
}
