package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.Ctx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.script.db.DbScriptManager;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.AppSqlMap;
import cn.geelato.web.platform.m.base.entity.CustomSql;
import cn.geelato.web.platform.m.base.enums.ApprovalStatusEnum;
import cn.geelato.web.platform.m.base.service.AppSqlMapService;
import cn.geelato.web.platform.m.base.service.SqlService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController("/sql")
@Slf4j
public class SqlController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<CustomSql> CLAZZ = CustomSql.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "keyName", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final SqlService sqlService;
    private final AppSqlMapService appSqlMapService;

    @Autowired
    public SqlController(SqlService sqlService, AppSqlMapService appSqlMapService) {
        this.sqlService = sqlService;
        this.appSqlMapService = appSqlMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return sqlService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<CustomSql>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            List<CustomSql> list = sqlService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(list);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<CustomSql> get(@PathVariable(required = true) String id) {
        try {
            CustomSql model = sqlService.getModel(CLAZZ, id);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<CustomSql> createOrUpdate(@RequestBody CustomSql form) {
        try {
            form.afterSet();
            CustomSql result = new CustomSql();
            if (Strings.isNotBlank(form.getId())) {
                result = sqlService.updateModel(form);
            } else {
                result = sqlService.createModel(form);
            }
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            CustomSql model = sqlService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            sqlService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody CustomSql form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("key_name", form.getKeyName());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            boolean isValid = sqlService.validate("platform_sql", form.getId(), params);
            return ApiResult.success(isValid);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/refresh/{sqlKey}", method = RequestMethod.GET)
    public ApiResult<NullResult> refresh(@PathVariable String sqlKey) {
        DbScriptManager dbScriptManager = DbScriptManagerFactory.get("db");
        try {
            dbScriptManager.refresh(sqlKey);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query/{appId}", method = RequestMethod.GET)
    public ApiResult<List<CustomSql>> queryByApp(@PathVariable(required = true) String appId) {
        try {
            List<CustomSql> customSqls = new ArrayList<>();
            // 应用下sql
            Map<String, Object> params = new HashMap<>();
            params.put("appId", appId);
            params.put("tenantCode", Ctx.getCurrentTenantCode());
            List<CustomSql> sqlList = sqlService.queryModel(CLAZZ, params);
            customSqls.addAll(sqlList);
            // 其他应用授权sql
            Map<String, Object> appParams = new HashMap<>();
            appParams.put("appId", appId);
            appParams.put("tenantCode", Ctx.getCurrentTenantCode());
            appParams.put("enableStatus", ColumnDefault.ENABLE_STATUS_VALUE);
            appParams.put("approvalStatus", ApprovalStatusEnum.AGREE.getValue());
            List<AppSqlMap> appSqlMaps = appSqlMapService.queryModel(AppSqlMap.class, appParams);
            if (appSqlMaps != null && !appSqlMaps.isEmpty()) {
                String sqlIds = appSqlMaps.stream().map(AppSqlMap::getSqlId).collect(Collectors.joining(","));
                FilterGroup filterGroup = new FilterGroup();
                filterGroup.addFilter("id", FilterGroup.Operator.in, sqlIds);
                List<CustomSql> appSqlList = sqlService.queryModel(CLAZZ, filterGroup);
                customSqls.addAll(appSqlList);
            }
            return ApiResult.success(customSqls);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
