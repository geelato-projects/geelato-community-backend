package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.meta.Role;
import cn.geelato.web.platform.m.security.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController(value = "/security/role")
@Slf4j
public class RoleRestController extends BaseController {
    private static final Class<Role> CLAZZ = Role.class;
    private final RoleService roleService;

    @Autowired
    public RoleRestController(RoleService roleService) {
        this.roleService = roleService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return roleService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    public ApiPagedResult pageQueryOf() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters();
            return roleService.pageQueryModel("page_query_platform_role_app", params, pageQueryRequest);
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
            return ApiResult.success(roleService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(roleService.getModel(id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody Role form) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(roleService.updateModel(form));
            } else {
                return ApiResult.success(roleService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            Role model = roleService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            roleService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Role form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("app_id", form.getAppId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(roleService.validate("platform_role", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
