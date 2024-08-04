package cn.geelato.web.platform.m.model.rest;

import cn.geelato.web.platform.m.model.service.DevViewService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import cn.geelato.web.platform.m.security.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.enums.ColumnSyncedEnum;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.meta.model.field.ColumnSelectType;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author diabl
 */
@Controller
@RequestMapping(value = "/api/model/table/column")
public class DevTableColumnController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<ColumnMeta> CLAZZ = ColumnMeta.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "fieldName", "name", "comment", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(DevTableColumnController.class);
    private final MetaManager metaManager = MetaManager.singleInstance();
    @Autowired
    private DevTableColumnService devTableColumnService;
    @Autowired
    private DevViewService devViewService;
    @Autowired
    private PermissionService permissionService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult<DataItems> pageQuery(HttpServletRequest req) {
        ApiPagedResult<DataItems> result = new ApiPagedResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = devTableColumnService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(devTableColumnService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult<>();
        try {
            result.setData(devTableColumnService.getModel(CLAZZ, id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody ColumnMeta form) {
        ApiResult result = new ApiResult<>();
        try {
            form.afterSet();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                // 存在，方可更新
                ColumnMeta meta = devTableColumnService.getModel(CLAZZ, form.getId());
                Assert.notNull(meta, ApiErrorMsg.IS_NULL);
                form = devTableColumnService.upgradeTable(form, meta);
                ColumnMeta resultMap = devTableColumnService.updateModel(form);
                if (!meta.getName().equalsIgnoreCase(form.getName())) {
                    permissionService.columnPermissionChangeObject(form.getTableName(), form.getName(), meta.getName());
                    permissionService.resetDefaultPermission(PermissionTypeEnum.COLUMN.getValue(), form.getTableName(), form.getAppId());
                }
                result.setData(resultMap);
            } else {
                form.setSynced(ColumnSyncedEnum.FALSE.getValue());
                ColumnMeta resultMap = devTableColumnService.createModel(form);
                permissionService.resetDefaultPermission(PermissionTypeEnum.COLUMN.getValue(), form.getTableName(), form.getAppId());
                result.setData(resultMap);
            }
            // 选择类型为 组织、用户时
            if (result.isSuccess()) {
                devTableColumnService.automaticGeneration(form);
            }
            // 刷新实体缓存
            if (result.isSuccess() && Strings.isNotEmpty(form.getTableName())) {
                // 刷新默认视图
                updateDefaultTableView(form.getTableId());
                metaManager.refreshDBMeta(form.getTableName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
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
    @ResponseBody
    public ApiResult<Map> insertCommon(@RequestBody Map<String, Object> params) {
        ApiResult<Map> result = new ApiResult<>();
        try {
            String tableId = String.valueOf(params.get("tableId"));
            String tableName = String.valueOf(params.get("tableName"));
            String columnIds = String.valueOf(params.get("columnIds"));
            if (Strings.isBlank(tableId) || Strings.isBlank(columnIds)) {
                return result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
            //
            TableMeta tableMeta = devTableColumnService.getModel(TableMeta.class, tableId);
            Assert.notNull(tableMeta, ApiErrorMsg.IS_NULL);
            // 需要添加的字段
            FilterGroup columnFilter = new FilterGroup();
            columnFilter.addFilter("id", FilterGroup.Operator.in, columnIds);
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(CLAZZ, columnFilter);
            if (columnMetas == null || columnMetas.size() == 0) {
                return result;
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
            if (result.isSuccess() && Strings.isNotEmpty(tableMeta.getEntityName())) {
                // 刷新默认视图
                updateDefaultTableView(tableMeta.getId());
                metaManager.refreshDBMeta(tableMeta.getEntityName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult isDelete(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
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
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryDefaultMeta", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult<List<ColumnMeta>> queryDefaultMeta() {
        ApiResult<List<ColumnMeta>> result = new ApiResult<>();
        try {
            List<ColumnMeta> defaultColumnMetaList = metaManager.getDefaultColumn();
            result.setData(defaultColumnMetaList);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/selectType", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult<List<ColumnSelectType>> getSelectType() {
        ApiResult<List<ColumnSelectType>> result = new ApiResult<>();
        try {
            List<ColumnSelectType> selectTypes = metaManager.getColumnSelectType();
            result.setData(selectTypes);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validate(@RequestBody ColumnMeta form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("column_name", form.getName());
            params.put("table_id", form.getTableId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            result.setData(devTableColumnService.validate("platform_dev_column", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }
}
