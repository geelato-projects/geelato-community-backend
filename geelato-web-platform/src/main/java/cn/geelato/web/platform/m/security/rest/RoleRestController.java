package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.security.entity.Role;
import cn.geelato.web.platform.m.security.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
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
@ApiRestController(value = "/security/role")
@Slf4j
public class RoleRestController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Role> CLAZZ = Role.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("name", "code", "appName", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final RoleService roleService;

    @Autowired
    public RoleRestController(RoleService roleService) {
        this.roleService = roleService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return roleService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    public ApiPagedResult pageQueryOf(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(req);
            return roleService.pageQueryModel("page_query_platform_role_app", params, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return ApiResult.success(roleService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(roleService.getModel(id));
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Role form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("app_id", form.getAppId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(roleService.validate("platform_role", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
