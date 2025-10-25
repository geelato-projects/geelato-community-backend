package cn.geelato.web.platform.srv.model;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.enums.ColumnSyncedEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableSourceTypeEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.model.service.DevTableColumnService;
import cn.geelato.web.platform.srv.model.service.DevTableService;
import cn.geelato.web.platform.srv.model.service.DevViewService;
import cn.geelato.web.platform.srv.security.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/model/table")
@Slf4j
public class DevTableController extends BaseController {
    private static final Class<TableMeta> CLAZZ = TableMeta.class;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final DevTableService devTableService;
    private final DevTableColumnService devTableColumnService;
    private final DevViewService devViewService;
    private final PermissionService permissionService;

    @Autowired
    public DevTableController(DevTableService devTableService, DevTableColumnService devTableColumnService, DevViewService devViewService, PermissionService permissionService) {
        this.devTableService = devTableService;
        this.devTableColumnService = devTableColumnService;
        this.devViewService = devViewService;
        this.permissionService = permissionService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() throws ParseException {
        Map<String, Object> requestBody = this.getRequestBody();
        PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
        FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
        return devTableService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<TableMeta>> query() {
        PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
        Map<String, Object> params = this.getQueryParameters(CLAZZ);
        return ApiResult.success(devTableService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<TableMeta> get(@PathVariable String id) {
        return ApiResult.success(devTableService.getModel(CLAZZ, id));
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<?> createOrUpdate(@RequestBody TableMeta form) {
        try {
            devTableService.afterSet(form);
            TableMeta resultMap = new TableMeta();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                // 存在，方可更新
                TableMeta model = devTableService.getModel(CLAZZ, form.getId());
                if (model != null) {
                    form = devTableService.handleForm(form, model);
                    resultMap = devTableService.updateModel(form);
                    if (!model.getEntityName().equals(form.getEntityName())) {
                        // 修正权限
                        permissionService.tablePermissionChangeObject(form.getEntityName(), model.getEntityName(), model.getConnectId());
                        // 添加默认权限
                        permissionService.resetTableDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getConnectId(), form.getAppId());
                    }
                    // 刷新默认视图
                    List<TableView> tableViewList = devViewService.getTableView(form.getConnectId(), form.getEntityName());
                    if (tableViewList != null && !tableViewList.isEmpty()) {
                        devViewService.createOrUpdateDefaultTableView(form, devTableColumnService.getDefaultViewSql(form.getEntityName()));
                    }
                } else {
                    throw new RuntimeException("update fail");
                }
            } else {
                form.setSynced(ColumnSyncedEnum.FALSE.getValue());
                resultMap = devTableService.createModel(form);
                form.setId(resultMap.getId());
                // 添加默认权限
                permissionService.resetDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getConnectId(), form.getAppId());
                // 添加默认字段，第三方数据源不添加默认字段
                if (!TableSourceTypeEnum.THIRD.getValue().equals(form.getSourceType())) {
                    devTableColumnService.createDefaultColumn(form);
                }
                return ApiResult.success(resultMap);
            }
            if (Strings.isNotEmpty(form.getEntityName())) {
                // 刷新实体缓存
                metaManager.refreshDBMeta(form.getEntityName());
            }
            return ApiResult.success(resultMap);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/copy", method = RequestMethod.POST)
    public ApiResult<TableMeta> copy(@RequestBody Map<String, Object> params) {
        try {
            String tableId = String.valueOf(params.get("id"));
            String title = String.valueOf(params.get("title"));
            String entityName = String.valueOf(params.get("entityName"));
            String connectId = String.valueOf(params.get("connectId"));
            String tableComment = String.valueOf(params.get("tableComment"));
            String appId = String.valueOf(params.get("appId"));
            String tenantCode = String.valueOf(params.get("tenantCode"));
            if (Strings.isBlank(entityName) || Strings.isBlank(tableId)) {
                throw new RuntimeException("entityName or tableId is null");
            }
            TableMeta form = devTableService.copyTable(tableId, title, entityName, connectId, tableComment, appId, tenantCode);
            // 添加默认权限
            permissionService.resetDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getConnectId(), form.getAppId());
            if (Strings.isNotEmpty(form.getEntityName())) {
                // 刷新默认视图
                // devViewService.createOrUpdateDefaultTableView(form, devTableColumnService.getDefaultViewSql(form.getEntityName()));
                // 刷新实体缓存
                metaManager.refreshDBMeta(form.getEntityName());
            }
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/upgradesTable", method = RequestMethod.POST)
    public ApiResult<?> upgradesTable(@RequestBody Map<String, Object> params) {
        try {
            String tableId = params.get("tableId") == null ? null : params.get("tableId").toString();
            String columnNames = params.get("columnNames") == null ? null : params.get("columnNames").toString();
            String upgradeType = params.get("upgradeType") == null ? null : params.get("upgradeType").toString();

            if (Strings.isBlank(tableId) || Strings.isBlank(columnNames)) {
                throw new RuntimeException("tableId or columnNames is null");
            }
            String[] columnNameArray = columnNames.split(",");
            Map<String, ColumnMeta> columnMetaMap = metaManager.getTableUpgradeList();
            Assert.notNull(columnMetaMap, "Upgrade template not found");
            TableMeta tableMeta = devTableService.getModel(CLAZZ, tableId);
            Assert.notNull(tableMeta, "Table not found");
            List<String> columnNameList = new ArrayList<>();
            Map<String, Object> columnParams = new HashMap<>();
            columnParams.put("tableId", tableMeta.getId());
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, columnParams);
            if (columnMetas != null && !columnMetas.isEmpty()) {
                columnNameList = columnMetas.stream().map(ColumnMeta::getFieldName).toList();
            }
            for (String columnName : columnNameArray) {
                if (!columnNameList.contains(columnName)) {
                    ColumnMeta columnMeta = columnMetaMap.get(columnName);
                    if (columnMeta != null) {
                        columnMeta.setTableId(tableMeta.getId());
                        columnMeta.setTableName(tableMeta.getEntityName());
                        columnMeta.setTableSchema(tableMeta.getTableSchema());
                        columnMeta.setAppId(tableMeta.getAppId());
                        columnMeta.setTenantCode(tableMeta.getTenantCode());
                        devTableColumnService.createModel(columnMeta);
                    } else {
                        throw new RuntimeException(columnName + " not found");
                    }
                }
            }
            if (Strings.isNotBlank(upgradeType)) {
                if (upgradeType.contains("app")) {
                    tableMeta.setAcrossApp(true);
                }
                if (upgradeType.contains("workflow")) {
                    tableMeta.setAcrossWorkflow(true);
                }
                devTableService.updateModel(tableMeta);
            }

            if (Strings.isNotEmpty(tableMeta.getEntityName())) {
                metaManager.refreshDBMeta(tableMeta.getEntityName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable String id) {
        try {
            TableMeta model = devTableService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            devTableService.isDeleteModel(model);
            // 刷新实体缓存
            if (Strings.isNotEmpty(model.getEntityName())) {
                metaManager.removeLiteMeta(model.getEntityName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryDefaultView/{entityName}", method = RequestMethod.GET)
    public ApiResult<String> queryDefaultView(@PathVariable String entityName) {
        try {
            Map<String, Object> viewParams = devTableColumnService.getDefaultViewSql(entityName);
            return ApiResult.success(String.valueOf(viewParams.get("viewConstruct")));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/resetDefaultView", method = RequestMethod.POST)
    public ApiResult<NullResult> resetDefaultView(@RequestBody TableMeta form) {
        try {
            if (Strings.isNotBlank(form.getEntityName())) {
                Assert.notNull(form, ApiErrorMsg.IS_NULL);
                Map<String, Object> params = new HashMap<>();
                params.put("id", form.getId());
                params.put("connectId", form.getConnectId());
                params.put("entityName", form.getEntityName());
                List<TableMeta> tableMetaList = devTableService.queryModel(CLAZZ, params);
                if (tableMetaList != null && !tableMetaList.isEmpty()) {
                    for (TableMeta meta : tableMetaList) {
                        devViewService.createOrUpdateDefaultTableView(meta, devTableColumnService.getDefaultViewSql(meta.getEntityName()));
                    }
                }
            } else {
                throw new RuntimeException("entityName is null");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = {"/reset/{tableId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<?> resetModelFormTable(@PathVariable("tableId") String tableId) {
        try {
            if (Strings.isNotBlank(tableId)) {
                // dev_table
                TableMeta model = devTableService.getModel(CLAZZ, tableId);
                Assert.notNull(model, ApiErrorMsg.IS_NULL);
                // 禁用的不同步
                if (EnableStatusEnum.DISABLED.getValue() == model.getEnableStatus()) {
                    throw new RuntimeException("Table is disabled, can not be reset");
                }
                devTableService.resetTableByDataBase(model);
            } else {
                throw new RuntimeException("tableId is null");
            }
            return ApiMetaResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiMetaResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody TableMeta form) {
        try {
            Map<String, String> lowers = new HashMap<>();
            lowers.put("entity_name", form.getEntityName());
            Map<String, String> params = new HashMap<>();
            params.put("connect_id", form.getConnectId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(devTableService.validate("platform_dev_table", form.getId(), params, lowers));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
