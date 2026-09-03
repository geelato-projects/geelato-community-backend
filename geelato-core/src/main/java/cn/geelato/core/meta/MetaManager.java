package cn.geelato.core.meta;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DataTypeRadiusEnum;
import cn.geelato.core.enums.MysqlToJavaEnum;
import cn.geelato.core.meta.spi.MetaDefinitionBundle;
import cn.geelato.core.meta.spi.MetaResourceProvider;
import cn.geelato.core.meta.spi.MetaStore;
import cn.geelato.core.meta.support.DefaultMetaResourceProvider;
import cn.geelato.lang.meta.Entity;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.entity.EntityLiteMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.utils.AnnotatedClassScanner;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    @Getter
    private MetaStore metaStore = null;
    @Getter
    private MetaResourceProvider metaResourceProvider = new DefaultMetaResourceProvider();
    /**
     * 同名实体（Java类源 vs DB在线源）冲突时的合并策略：
     * <ul>
     *     <li>DATABASE：以在线DB定义为准（覆盖字段集），适合在线实体由设计器维护最新真值的场景</li>
     *     <li>CLASS：以Java类定义为准（兼容历史行为）</li>
     * </ul>
     * catalog=platform 的系统内置实体始终以 Java 类为准，不受此配置影响。
     */
    @Getter
    private ConflictStrategy conflictStrategy = ConflictStrategy.CLASS;
    /**
     * 冲突检测总开关，默认关闭。对应配置项 geelato.meta.conflict-detect.enabled。
     * <p>
     * 关闭时：parseOne/parseTableEntity/parseDBMeta 保持原有静默"先到先得"逻辑，不输出冲突告警、不改变覆盖行为，与改动前完全一致。
     * 开启时：才输出冲突 warn 日志、启用 diff 日志、按 conflictStrategy 处理同名冲突。
     * </p>
     */
    @Getter
    @Setter
    private boolean conflictDetectEnabled = false;
    /**
     * catalog（逻辑数据库分组）到数据源 connectId 的映射。
     * <p>
     * 实体的 {@code @Entity(catalog)} 值在此查表得到数据源 key，使 catalog 承担"划分数据库"的职责。
     * 对应配置项 {@code geelato.datasource.dynamic.catalog-mapping}。默认空，表示不启用 catalog 路由。
     * </p>
     */
    @Getter
    private Map<String, String> catalogConnectIdMapping = new HashMap<>();
    /**
     * 已完成 classpath 实体扫描的包集合，用于跨组件去重（如 geelato-orm 的 OrmAutoConfiguration 重复扫描同一批 @Entity）。
     * 判定子包关系：已扫包是请求包的祖先包时视为已覆盖。
     */
    @Getter
    private final Set<String> scannedPackages = ConcurrentHashMap.newKeySet();

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
     */
    public void parseDBMeta() {
        parseDBMeta(null);
    }

    /**
     * 根据需求刷新模型和视图。
     * <br>根据传入的参数，从数据库中查询表信息、列信息和视图信息，并对这些信息进行处理以刷新模型和视图。
     *
     * @param params 包含查询参数的Map，支持的参数包括appId、connectId、tableId和entityName
     */
    public void parseDBMeta(Map<String, String> params) {
        // 业务层未提供 MetaStore 时（框架独立运行），跳过数据库元数据加载。
        if (metaStore == null) {
            log.info("parse meta data in database... skipped (no MetaStore provided)");
            return;
        }
        log.info("parse meta data in database...");
        MetaDefinitionBundle definitionBundle = metaStore.load(params);
        List<TableMeta> tableList = definitionBundle.getTableList();
        List<ColumnMeta> allColumnList = definitionBundle.getColumnList();
        List<TableView> allViewList = definitionBundle.getViewList();
        List<TableCheck> allCheckList = definitionBundle.getCheckList();
        List<TableForeign> allForeignList = definitionBundle.getForeignList();
        // 预建索引，避免对每张表在全量列/视图/检查/外键列表上做 O(n) 线性扫描（原为 O(T×C)）。
        Map<String, List<ColumnMeta>> columnsByTableId = allColumnList.stream()
                .collect(Collectors.groupingBy(x -> String.valueOf(x.getTableId())));
        Map<String, List<TableView>> viewsByEntityConnect = allViewList.stream()
                .filter(v -> v.getEntityName() != null && v.getConnectId() != null)
                .collect(Collectors.groupingBy(v -> v.getEntityName() + "\0" + v.getConnectId()));
        Map<String, List<TableCheck>> checksByTableId = allCheckList.stream()
                .collect(Collectors.groupingBy(x -> String.valueOf(x.getTableId())));
        Map<String, List<TableForeign>> foreignsByMainTable = allForeignList.stream()
                .filter(f -> f.getMainTable() != null)
                .collect(Collectors.groupingBy(TableForeign::getMainTable));
        for (TableMeta tableMeta : tableList) {
            String tableId = tableMeta.getId();
            String entityName = tableMeta.getEntityName();
            String connectId = tableMeta.getConnectId();
            if (StringUtils.isAnyBlank(tableId, entityName, connectId)) {
                continue;
            }
            List<ColumnMeta> columnList = columnsByTableId.getOrDefault(tableId, Collections.emptyList());
            List<TableView> viewList = viewsByEntityConnect.getOrDefault(entityName + "\0" + connectId, Collections.emptyList());
            List<TableCheck> checkList = checksByTableId.getOrDefault(tableId, Collections.emptyList());
            List<TableForeign> foreignList = foreignsByMainTable.getOrDefault(entityName, Collections.emptyList());
            parseTableEntity(tableMeta, columnList, viewList, checkList, foreignList);
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
        List<TableView> viewList = metaStore.loadByViewName(viewName).getViewList();
        for (TableView view : viewList) {
            removeOne(viewName);
            parseViewEntity(view);
        }
    }

    /**
     * 刷新表元数据
     *
     * @param entityName 实体名称
     */
    private void refreshTableMeta(String entityName) {
        MetaDefinitionBundle definitionBundle = metaStore.loadByEntityName(entityName);
        List<TableMeta> tableList = definitionBundle.getTableList();
        for (TableMeta tableMeta : tableList) {
            List<ColumnMeta> columnList = definitionBundle.getColumnList();
            List<TableView> viewList = definitionBundle.getViewList();
            List<TableCheck> checkList = definitionBundle.getCheckList();
            List<TableForeign> foreignList = definitionBundle.getForeignList();
            removeOne(entityName);
            parseTableEntity(tableMeta, columnList, viewList, checkList, foreignList);
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
            log.warn("Key({}) not found in entityMetadataMap by entityName, registered={}", entityName, entityMetadataMap.size());
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
            log.warn("Key({}) not found in tableNameMetadataMap by tableName, registered={}", tableName, tableNameMetadataMap.size());
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
     * 判断给定包是否已被扫描覆盖（自身或其祖先包已扫描过）。
     * 供 geelato-orm 等组件去重，避免对同一批 @Entity 重复进行 classpath 遍历与类加载。
     *
     * @param packageName 待判断的包名
     * @return 已被覆盖返回 true
     */
    public boolean isPackageAlreadyScanned(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        for (String scanned : scannedPackages) {
            if (packageName.equals(scanned) || packageName.startsWith(scanned + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检索批定包名中包含所有的包javax.persistence.Entity的类，并进行解析
     */
    private void scanAndParse(String packageName) {
        if (isPackageAlreadyScanned(packageName)) {
            log.debug("包{}已被扫描覆盖，跳过重复扫描", packageName);
            return;
        }
        log.debug("开始从包{}中扫描到包含注解{}的实体......", packageName, Entity.class);
        List<Class<?>> classes = AnnotatedClassScanner.scan(packageName, Entity.class);
        scannedPackages.add(packageName);
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

    public void parseTableEntity(TableMeta tableMeta, List<ColumnMeta> columnList, List<TableView> viewList, List<TableCheck> checkList, List<TableForeign> foreignList) {
        String entityName = tableMeta.getEntityName();
        EntityMeta entityMeta = null;
        if (Strings.isNotBlank(entityName)) {
            entityMeta = MetaReflex.getEntityMetaByTable(tableMeta, columnList, viewList, checkList, foreignList);
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
            } else if (entityMetadataMap.containsKey(entityName)) {
                entityMeta = entityMetadataMap.get(entityName);
                if (entityMeta != null && entityMeta.getTableMeta() != null) {
                    entityMeta.setTableMeta(tableMeta);
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
            return;
        }
        // 无冲突：正常注册在线实体
        if (!entityMetadataMap.containsKey(entityName)) {
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.Table));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
        }
    }

    public void parseViewEntity(List<TableView> viewList) {
        for (TableView view : viewList) {
            parseViewEntity(view);
        }
    }

    public void parseViewEntity(TableView view) {
        if (view == null || view.getViewName() == null) {
            return;
        }
        String entityName = view.getViewName();
        if (Strings.isNotBlank(entityName) && !entityMetadataMap.containsKey(entityName)) {
            EntityMeta entityMeta = MetaReflex.getEntityMetaByView(view);
            entityMetadataMap.put(entityMeta.getEntityName(), entityMeta);
            removeLiteMeta(entityMeta.getEntityName());
            entityLiteMetaList.add(new EntityLiteMeta(entityMeta.getEntityName(), entityMeta.getEntityTitle(), EntityType.View));
            tableNameMetadataMap.put(entityMeta.getTableName(), entityMeta);
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

    public void setMetaStore(MetaStore metaStore) {
        if (metaStore != null) {
            this.metaStore = metaStore;
        }
    }

    public void setMetaResourceProvider(MetaResourceProvider metaResourceProvider) {
        if (metaResourceProvider != null) {
            this.metaResourceProvider = metaResourceProvider;
        }
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        if (conflictStrategy != null) {
            this.conflictStrategy = conflictStrategy;
        }
    }

    public void setCatalogConnectIdMapping(Map<String, String> catalogConnectIdMapping) {
        if (catalogConnectIdMapping != null) {
            this.catalogConnectIdMapping = catalogConnectIdMapping;
        }
    }

    /**
     * 运行时统一解析实体的数据源 connectId（查询期即时解析，规避扫描期 mapping 未注入的时序问题）。
     * <p>优先级：</p>
     * <ol>
     *     <li>{@code TableMeta.connectId}：来自 @Entity(connectId) 或数据库登记值（platform_dev_table.connect_id）</li>
     *     <li>{@code @Entity(catalog)} 在 catalogConnectIdMapping 中的映射值</li>
     *     <li>返回 null，由调用方回退到默认数据源 primary</li>
     * </ol>
     *
     * @param entityName 实体名称
     * @return 数据源 key，未解析到返回 null
     */
    public String resolveConnectId(String entityName) {
        if (!StringUtils.isNotBlank(entityName)) {
            return null;
        }
        EntityMeta entityMeta = getByEntityName(entityName);
        if (entityMeta == null) {
            return null;
        }
        // 优先级1：TableMeta.connectId（@Entity(connectId) 显式指定 或 DB 登记值）
        if (entityMeta.getTableMeta() != null
                && StringUtils.isNotBlank(entityMeta.getTableMeta().getConnectId())) {
            return entityMeta.getTableMeta().getConnectId();
        }
        // 优先级2：catalog 映射到数据源
        String catalog = entityMeta.getCatalog();
        if (StringUtils.isNotBlank(catalog)) {
            String mappedConnectId = catalogConnectIdMapping.get(catalog);
            if (StringUtils.isNotBlank(mappedConnectId)) {
                return mappedConnectId;
            }
        }
        return null;
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
