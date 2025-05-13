package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.ColumnSyncedEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import cn.geelato.web.platform.m.model.service.DevViewService;
import cn.geelato.web.platform.m.security.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/model/table/column")
@Slf4j
public class DevTableColumnController extends BaseController {
    private static final Class<ColumnMeta> CLAZZ = ColumnMeta.class;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final DevTableColumnService devTableColumnService;
    private final DevViewService devViewService;
    private final PermissionService permissionService;

    @Autowired
    public DevTableColumnController(DevTableColumnService devTableColumnService, DevViewService devViewService, PermissionService permissionService) {
        this.devTableColumnService = devTableColumnService;
        this.devViewService = devViewService;
        this.permissionService = permissionService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return devTableColumnService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(devTableColumnService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(devTableColumnService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody ColumnMeta form) {
        try {
            form.afterSet();
            ColumnMeta resultMap = new ColumnMeta();
            TableMeta tableMeta = devTableColumnService.getModel(TableMeta.class, form.getTableId());
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                // 存在，方可更新
                ColumnMeta meta = devTableColumnService.getModel(CLAZZ, form.getId());
                Assert.notNull(meta, ApiErrorMsg.IS_NULL);
                form = devTableColumnService.upgradeTable(tableMeta, form, meta);
                resultMap = devTableColumnService.updateModel(form);
                if (!meta.getName().equalsIgnoreCase(form.getName())) {
                    permissionService.columnPermissionChangeObject(tableMeta.getConnectId(), form.getTableName(), form.getName(), meta.getName());
                    permissionService.resetDefaultPermission(PermissionTypeEnum.COLUMN.getValue(), form.getTableName(), tableMeta.getConnectId(), form.getAppId());
                }
            } else {
                form.setSynced(ColumnSyncedEnum.FALSE.getValue());
                resultMap = devTableColumnService.createModel(form);
                permissionService.resetDefaultPermission(PermissionTypeEnum.COLUMN.getValue(), form.getTableName(), tableMeta.getConnectId(), form.getAppId());
            }
            // 选择类型为 组织、用户时
            devTableColumnService.automaticGeneration(form);
            // 刷新实体缓存
            if (Strings.isNotEmpty(form.getTableName())) {
                // 刷新默认视图
                updateDefaultTableView(form.getTableId());
                metaManager.refreshDBMeta(form.getTableName());
            }
            return ApiResult.success(resultMap);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private void updateDefaultTableView(String tableId) {
        TableMeta form = devTableColumnService.getModel(TableMeta.class, tableId);
        if (form != null) {
            List<TableView> tableViewList = devViewService.getTableView(form.getConnectId(), form.getEntityName());
            if (tableViewList != null && tableViewList.size() > 0) {
                devViewService.createOrUpdateDefaultTableView(form, devTableColumnService.getDefaultViewSql(form.getEntityName()));
            }
        }
    }

    @RequestMapping(value = "/insertCommon", method = RequestMethod.POST)
    public ApiResult<NullResult> insertCommon(@RequestBody Map<String, Object> params) {
        try {
            String tableId = String.valueOf(params.get("tableId"));
            String tableName = String.valueOf(params.get("tableName"));
            String columnIds = String.valueOf(params.get("columnIds"));
            if (Strings.isBlank(tableId) || Strings.isBlank(columnIds)) {
                throw new RuntimeException("tableId or columnIds is null");
            }
            //
            TableMeta tableMeta = devTableColumnService.getModel(TableMeta.class, tableId);
            Assert.notNull(tableMeta, ApiErrorMsg.IS_NULL);
            // 需要添加的字段
            FilterGroup columnFilter = new FilterGroup();
            columnFilter.addFilter("id", FilterGroup.Operator.in, columnIds);
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(CLAZZ, columnFilter);
            if (columnMetas == null || columnMetas.size() == 0) {
                throw new RuntimeException("columnMetas is null");
            }
            // 已有字段，
            List<String> existColumnNames = new ArrayList<>();
            Map<String, Object> columnParams = new HashMap<>();
            columnParams.put("tableId", tableId);
            List<ColumnMeta> existColumns = devTableColumnService.queryModel(CLAZZ, columnParams);
            if (existColumns != null && existColumns.size() > 0) {
                for (ColumnMeta meta : existColumns) {
                    if (!existColumnNames.contains(meta.getName())) {
                        existColumnNames.add(meta.getName());
                    }
                }
            }
            // 添加字段
            for (ColumnMeta meta : columnMetas) {
                if (!existColumnNames.contains(meta.getName())) {
                    meta.setId(null);
                    meta.setTableId(tableMeta.getId());
                    meta.setTableName(tableMeta.getEntityName());
                    meta.setAppId(tableMeta.getAppId());
                    meta.setTenantCode(tableMeta.getTenantCode());
                    meta.setSynced(ColumnSyncedEnum.FALSE.getValue());
                    devTableColumnService.createModel(meta);
                }
            }
            // 刷新实体缓存
            if (Strings.isNotEmpty(tableMeta.getEntityName())) {
                // 刷新默认视图
                updateDefaultTableView(tableMeta.getId());
                metaManager.refreshDBMeta(tableMeta.getEntityName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            ColumnMeta model = devTableColumnService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            devTableColumnService.isDeleteModel(model);
            // 刷新实体缓存
            if (Strings.isNotEmpty(model.getTableName())) {
                // 刷新默认视图
                updateDefaultTableView(model.getTableId());
                metaManager.refreshDBMeta(model.getTableName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryDefaultMeta", method = RequestMethod.GET)
    public ApiResult<List<ColumnMeta>> queryDefaultMeta() {
        try {
            List<ColumnMeta> defaultColumnMetaList = metaManager.getDefaultColumn();
            return ApiResult.success(defaultColumnMetaList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/selectType", method = RequestMethod.GET)
    public ApiResult<List<ColumnSelectType>> getSelectType() {
        try {
            List<ColumnSelectType> selectTypes = metaManager.getColumnSelectType();
            return ApiResult.success(selectTypes);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody ColumnMeta form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("column_name", form.getName());
            params.put("table_id", form.getTableId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(devTableColumnService.validate("platform_dev_column", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
