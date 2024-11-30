package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.enums.ColumnSyncedEnum;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import cn.geelato.web.platform.m.model.service.DevTableService;
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

import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/model/table")
@Slf4j
public class DevTableController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<TableMeta> CLAZZ = TableMeta.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "tableName", "entityName", "description"));
        OPERATORMAP.put("consists", List.of("connectId"));
        OPERATORMAP.put("isNulls", List.of("appId"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

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

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return devTableService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<TableMeta>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(devTableService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<TableMeta> get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(devTableService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody TableMeta form, Boolean isAlter) {
        try {
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
                        permissionService.tablePermissionChangeObject(form.getEntityName(), model.getEntityName());
                        // 添加默认权限
                        permissionService.resetTableDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getAppId());
                    }
                    // 刷新默认视图
                    List<TableView> tableViewList = devViewService.getTableView(form.getConnectId(), form.getEntityName());
                    if (tableViewList != null && tableViewList.size() > 0) {
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
                permissionService.resetDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getAppId());
                devTableColumnService.createDefaultColumn(form);
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
            permissionService.resetDefaultPermission(PermissionTypeEnum.getTablePermissions(), form.getEntityName(), form.getAppId());
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


    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
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
    public ApiResult<String> queryDefaultView(@PathVariable(required = true) String entityName) {
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
                if (tableMetaList != null && tableMetaList.size() > 0) {
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
    public ApiMetaResult resetModelFormTable(@PathVariable("tableId") String tableId) {
        try {
            if (Strings.isNotBlank(tableId)) {
                // dev_table
                TableMeta model = devTableService.getModel(CLAZZ, tableId);
                Assert.notNull(model, ApiErrorMsg.IS_NULL);
                // 禁用的不同步
                if (EnableStatusEnum.DISABLED.getCode() == model.getEnableStatus()) {
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
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(devTableService.validate("platform_dev_table", form.getId(), params, lowers));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
