package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableSourceTypeEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.meta.schema.SchemaCheck;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.DbGenerateDao;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import cn.geelato.web.platform.m.model.service.DevTableService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;

@Component
@Slf4j
public class MetaDdlService {
    @Lazy
    @Autowired
    protected DbGenerateDao dbGenerateDao;
    @Lazy
    @Autowired
    private DevTableService devTableService;
    @Lazy
    @Autowired
    private DevTableColumnService devTableColumnService;
    @Lazy
    @Autowired
    private ViewService viewService;
    private final MetaManager metaManager = MetaManager.singleInstance();

    public void createOrUpdateTableByEntityName(Dao primaryDao, String entityName, boolean dropBeforeCreate) {
        // 元数据
        EntityMeta entityMeta = metaManager.getByEntityName(entityName, dropBeforeCreate);
        if (entityMeta == null || entityMeta.getTableMeta() == null || entityMeta.getFieldMetas() == null || entityMeta.getFieldMetas().isEmpty()) {
            throw new RuntimeException("实体元数据为空");
        }
        TableMeta tableMeta = getTableMeta(entityMeta);
        // 切换数据库
        switchDbByConnectId(tableMeta.getConnectId());
        createOrUpdateTableByEntityMeta(primaryDao, entityMeta);
    }

    private TableMeta getTableMeta(EntityMeta entityMeta) {
        TableMeta tableMeta = entityMeta.getTableMeta();
        if (Strings.isBlank(tableMeta.getId()) || Strings.isBlank(tableMeta.getConnectId())) {
            FilterGroup filterGroup = new FilterGroup();
            filterGroup.addFilter("entityName", tableMeta.getEntityName());
            List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, filterGroup);
            if (tableMetas == null || tableMetas.isEmpty() || tableMetas.size() > 1) {
                throw new RuntimeException("实体元数据为空或多个");
            }
            entityMeta.setTableMeta(tableMetas.get(0));
        }
        return entityMeta.getTableMeta();
    }

    public void createOrUpdateTableByEntityMeta(Dao primaryDao, EntityMeta entityMeta) {
        TableMeta tableMeta = entityMeta.getTableMeta();
        String tableName = Strings.isEmpty(tableMeta.getTableName()) ? tableMeta.getEntityName() : tableMeta.getTableName();
        List<ColumnMeta> columnMetas = getColumnMetasByFieldMetas(entityMeta.getFieldMetas());
        Collection<TableCheck> tableChecks = entityMeta.getTableChecks();
        // 与元数据对比，判断字段名称是否一修改了。
        // setBeforeFieldName(tableMeta, columnMetas);
        // 切换数据库
        // switchDbByConnectId(tableMeta.getConnectId());
        // 判断表是否存在,表所属数据库
        Map<String, Object> dbColumnMap = dbGenerateDao.getDbTableColumns(tableMeta.getTableSchema(), tableName);
        // 表约束
        List<SchemaCheck> schemaCheckList = dbGenerateDao.queryTableChecks(tableMeta.getTableSchema(), tableName, tableChecks);
        // 表唯一约束
        List<SchemaIndex> schemaIndexList = dbGenerateDao.queryIndexes(tableName);
        // 比较
        Map<String, Object> map = dbGenerateDao.compareFields(tableMeta, columnMetas, tableChecks, dbColumnMap, schemaCheckList, schemaIndexList);
        // 是否存在表
        boolean isExistsTable = dbColumnMap != null && !dbColumnMap.isEmpty();
        // 执行操作
        dbGenerateDao.createOrUpdateTable(primaryDao, isExistsTable, map);
    }

    private List<ColumnMeta> getColumnMetasByFieldMetas(Collection<FieldMeta> fieldMetas) {
        List<ColumnMeta> columnMetas = new ArrayList<>();
        if (fieldMetas == null || fieldMetas.isEmpty()) {
            return columnMetas;
        }
        for (FieldMeta fieldMeta : fieldMetas) {
            ColumnMeta columnMeta = fieldMeta.getColumn();
            if (columnMeta != null) {
                columnMetas.add(columnMeta);
            }
        }
        return columnMetas;
    }

    private List<ColumnMeta> setBeforeFieldName(TableMeta tableMeta, List<ColumnMeta> fieldMetas) {
        // 新的数据
        if (fieldMetas == null || fieldMetas.isEmpty()) {
            return fieldMetas;
        }
        String tableName = Strings.isEmpty(tableMeta.getTableName()) ? tableMeta.getEntityName() : tableMeta.getTableName();
        // 查询元数据，旧的数据
        Map<String, Object> columnMap = new LinkedHashMap<>();
        columnMap.put("tableName", tableName);
        columnMap.put("tableId", tableMeta.getId());
        List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, columnMap);
        if (columnMetas == null || columnMetas.isEmpty()) {
            return fieldMetas;
        }
        // 比较
        for (ColumnMeta fieldMeta : fieldMetas) {
            for (ColumnMeta cMeta : columnMetas) {
                if (fieldMeta.getId().equals(cMeta.getId()) && !fieldMeta.getName().equals(cMeta.getName())) {
                    fieldMeta.setBefColName(cMeta.getName());
                    break;
                }
            }
        }
        return fieldMetas;
    }

    public ApiMetaResult createOrUpdateTableByAppId(Dao primaryDao, String appId, String tenantCode) {
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String errorModel = "";
        try {
            if (Strings.isNotBlank(appId)) {
                // 查询表元数据
                FilterGroup filterGroup = new FilterGroup();
                filterGroup.addFilter("appId", appId);
                filterGroup.addFilter("tenantCode", tenantCode);
                filterGroup.addFilter("enableStatus", String.valueOf(EnableStatusEnum.ENABLED.getCode()));
                List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, filterGroup);
                if (tableMetas == null || tableMetas.isEmpty()) {
                    return ApiMetaResult.successNoResult();
                }
                tableMetas.sort(new Comparator<TableMeta>() {
                    @Override
                    public int compare(TableMeta o1, TableMeta o2) {
                        return o1.getEntityName().compareToIgnoreCase(o2.getEntityName());
                    }
                });
                String currentConnect = "";
                // 创建或更新模型
                for (int i = 0; i < tableMetas.size(); i++) {
                    TableMeta tableMeta = tableMetas.get(i);
                    if (!TableSourceTypeEnum.CREATION.getValue().equalsIgnoreCase(tableMeta.getSourceType())) {
                        tableResult.put(tableMeta.getEntityName(), "ignore");
                        continue;
                    }
                    tableResult.put(tableMeta.getEntityName(), false);
                    // 数据库链接
                    if (Strings.isBlank(tableMeta.getConnectId())) {
                        throw new RuntimeException("数据库链接不能为空");
                    }
                    // 元数据
                    EntityMeta entityMeta = metaManager.getByEntityName(tableMeta.getEntityName(), false);
                    if (entityMeta == null || entityMeta.getTableMeta() == null || entityMeta.getFieldMetas() == null || entityMeta.getFieldMetas().isEmpty()) {
                        throw new RuntimeException("实体元数据为空(缓存)");
                    }
                    if (Strings.isBlank(entityMeta.getTableMeta().getConnectId())) {
                        entityMeta.setTableMeta(tableMeta);
                    }
                    errorModel = String.format("Error Model: %s（%s）", tableMeta.getTitle(), tableMeta.getEntityName());
                    // 切换数据
                    if (!currentConnect.equals(tableMeta.getConnectId())) {
                        switchDbByConnectId(tableMeta.getConnectId());
                        currentConnect = tableMeta.getConnectId();
                    }
                    // 创建或更新模型
                    createOrUpdateTableByEntityMeta(primaryDao, entityMeta);
                    tableResult.put(tableMeta.getEntityName(), true);
                }
            }
            return ApiMetaResult.success(tableResult);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            return ApiMetaResult.fail(tableResult, String.format("%s, %s", errorModel, message));
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                metaManager.parseDBMeta(primaryDao, table);
            }
        }
    }

    public ApiMetaResult createOrUpdateViewByAppId(Dao primaryDao, String appId, String tenantCode) {
        if (Strings.isBlank(appId)) {
            return ApiMetaResult.fail("参数错误，appId不能为空");
        }
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String errorModel = "";
        try {
            FilterGroup filterGroup = new FilterGroup();
            filterGroup.addFilter("tenantCode", tenantCode);
            List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, filterGroup);
            filterGroup.addFilter("enableStatus", String.valueOf(EnableStatusEnum.ENABLED.getCode()));
            filterGroup.addFilter("appId", appId);
            List<TableView> viewMetas = viewService.queryModel(TableView.class, filterGroup);
            if (viewMetas == null || viewMetas.isEmpty()) {
                return ApiMetaResult.successNoResult();
            }
            viewMetas.sort(new Comparator<TableView>() {
                @Override
                public int compare(TableView o1, TableView o2) {
                    return o1.getViewName().compareToIgnoreCase(o2.getViewName());
                }
            });
            for (TableView meta : viewMetas) {
                tableResult.put(meta.getViewName(), false);
            }
            String currentConnect = "";
            for (int i = 0; i < viewMetas.size(); i++) {
                TableView viewMeta = viewMetas.get(i);
                Optional<TableMeta> tableMetaResult = tableMetas.stream().filter(t -> t.getEntityName().equalsIgnoreCase(viewMeta.getEntityName())).findFirst();
                if (tableMetaResult.isEmpty()) {
                    tableResult.put(viewMeta.getViewName(), "不存在可以关联的数据库表");
                    continue;
                }
                // 验证视图
                handleValidateView(viewMeta, tableMetaResult.get());
                // 验证sql
                handleValidateViewSql(primaryDao, viewMeta.getConnectId(), viewMeta.getViewConstruct());
                // 切换数据
                if (!currentConnect.equals(viewMeta.getConnectId())) {
                    switchDbByConnectId(viewMeta.getConnectId());
                    currentConnect = viewMeta.getConnectId();
                }
                // 创建或更新视图
                dbGenerateDao.createOrUpdateView(viewMeta.getViewName(), viewMeta.getViewConstruct());
                tableResult.put(viewMeta.getViewName(), true);
            }
            return ApiMetaResult.success(tableResult);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            return ApiMetaResult.fail(tableResult, String.format("%s, %s", errorModel, message));
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                metaManager.parseDBMeta(primaryDao, table);
            }
        }
    }

    public ApiMetaResult createOrUpdateViewById(Dao primaryDao, String viewId) {
        if (Strings.isBlank(viewId)) {
            throw new RuntimeException("视图ID不能为空");
        }
        String entityName = null;
        try {
            // 视图信息
            TableView viewMeta = viewService.getModel(TableView.class, viewId);
            Assert.notNull(viewMeta, "视图信息查询失败");
            // 视图所属模型信息
            Map<String, Object> tableParams = new HashMap<>();
            tableParams.put("entityName", viewMeta.getEntityName());
            List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, tableParams);
            if (tableMetas == null || tableMetas.isEmpty()) {
                throw new RuntimeException("不存在可以关联的数据库表");
            }
            TableMeta tableMeta = tableMetas.get(0);
            // 验证视图
            handleValidateView(viewMeta, tableMeta);
            // 验证sql
            handleValidateViewSql(primaryDao, viewMeta.getConnectId(), viewMeta.getViewConstruct());
            // 切换数据库
            switchDbByConnectId(viewMeta.getConnectId());
            // 创建或更新视图
            dbGenerateDao.createOrUpdateView(viewMeta.getViewName(), viewMeta.getViewConstruct());
            entityName = viewMeta.getViewName();
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ApiMetaResult.fail(getMessage(ex));
        } finally {
            if (Strings.isNotBlank(entityName)) {
                metaManager.refreshDBMeta(entityName);
            }
        }
    }

    public ApiMetaResult createOrUpdateViewByEntity(Dao primaryDao, String entityName, Map<String, String> params) {
        try {
            String connectId = params.get("connectId");
            String sql = params.get("sql");
            // 验证sql
            handleValidateViewSql(primaryDao, connectId, sql);
            // 切换数据库
            switchDbByConnectId(connectId);
            // 创建或更新视图
            dbGenerateDao.createOrUpdateView(entityName, sql);
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiMetaResult.fail(getMessage(ex));
        } finally {
            if (Strings.isNotBlank(entityName)) {
                metaManager.refreshDBMeta(entityName);
            }
        }
    }

    private void handleValidateView(TableView viewMeta, TableMeta tableMeta) {
        // 验证
        if (!tableMeta.getSynced()) {
            throw new RuntimeException("模型与数据库表不一致，需同步");
        }
        if (Strings.isBlank(viewMeta.getViewConstruct())) {
            throw new RuntimeException("视图语句不存在");
        }
        if (Strings.isBlank(viewMeta.getViewName())) {
            throw new RuntimeException("视图名称不存在");
        }
        if (Strings.isBlank(viewMeta.getConnectId())) {
            throw new RuntimeException("数据库连接不存在");
        }
    }

    private void handleValidateViewSql(Dao primaryDao, String connectId, String sql) {
        // 验证视图语句
        boolean isValid = false;
        try {
            isValid = validateViewSql(primaryDao, connectId, sql);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            isValid = false;
        }
        if (!isValid) {
            throw new RuntimeException("视图语句验证失败");
        }
    }

    public boolean validateViewSql(Dao primaryDao, String connectId, String sql) {
        return dbGenerateDao.validateViewSql(primaryDao, connectId, sql);
    }

    public void refreshRedis(Dao primaryDao, Map<String, String> params) {
        Map<String, String> table = new HashMap<>();
        table.put("id", params.get("tableId"));
        table.put("entity_name", params.get("entityName"));
        table.put("connect_id", params.get("connectId"));
        table.put("app_id", params.get("appId"));
        table.put("tenant_code", Strings.isNotBlank(params.get("tenantCode")) ? params.get("tenantCode") : SessionCtx.getCurrentTenantCode());
        metaManager.parseDBMeta(primaryDao, table);
    }

    public void switchDbByConnectId(String connectId) {
        if (Strings.isBlank(connectId)) {
            throw new IllegalArgumentException("数据连接不能为空");
        }
        DynamicDatasourceHolder.setDataSourceKey(connectId);
    }


    public <T extends Exception> String getMessage(T ex) {
        return ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
    }
}
