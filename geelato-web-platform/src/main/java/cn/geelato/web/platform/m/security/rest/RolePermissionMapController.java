package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.entity.RolePermissionMap;
import cn.geelato.web.platform.m.security.service.RolePermissionMapService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController(value = "/security/role/permission")
@Slf4j
public class RolePermissionMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<RolePermissionMap> CLAZZ = RolePermissionMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("roleName", "permissionName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final RolePermissionMapService rolePermissionMapService;

    @Autowired
    public RolePermissionMapController(RolePermissionMapService rolePermissionMapService) {
        this.rolePermissionMapService = rolePermissionMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            return rolePermissionMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    public ApiPagedResult pageQueryOf() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters();
            return rolePermissionMapService.pageQueryModel("page_query_platform_role_r_permission", params, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(rolePermissionMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult insert(@RequestBody RolePermissionMap form) {
        try {
            return ApiResult.success(rolePermissionMapService.insertModels(form));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    public ApiResult<NullResult> switchModel(@RequestBody RolePermissionMap form) {
        try {
            rolePermissionMapService.switchModel(form);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            RolePermissionMap model = rolePermissionMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            rolePermissionMapService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryTable/{type}/{object}", method = RequestMethod.GET)
    public ApiResult queryTablePermissions(@PathVariable(required = true) String type, @PathVariable(required = true) String object, String appId, String tenantCode) {
        try {
            return ApiResult.success(rolePermissionMapService.queryTablePermissions(type, object, appId, tenantCode));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryColumn/{type}/{object}", method = RequestMethod.GET)
    public ApiResult queryColumnPermissions(@PathVariable(required = true) String type, @PathVariable(required = true) String object, String appId, String tenantCode) {
        try {
            return ApiResult.success(rolePermissionMapService.queryColumnPermissions(type, object, appId, tenantCode));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insertTable", method = RequestMethod.POST)
    public ApiResult<NullResult> insertTablePermission(@RequestBody RolePermissionMap form) {
        try {
            rolePermissionMapService.insertTablePermission(form);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insertTable/view", method = RequestMethod.POST)
    public ApiResult<NullResult> insertTableViewPermission(@RequestBody RolePermissionMap form) {
        try {
            if (Strings.isNotBlank(form.getPermissionId())) {
                Permission permission = rolePermissionMapService.getModel(Permission.class, form.getPermissionId());
                if (permission != null) {
                    form.setAppId(permission.getAppId());
                }
            }
            rolePermissionMapService.insertTableViewPermission(form);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insertColumn", method = RequestMethod.POST)
    public ApiResult<NullResult> insertColumnPermission(@RequestBody Map<String, Object> form) {
        try {
            String roleId = (String) form.get("roleId");
            String columnId = (String) form.get("columnId");
            String rule = (String) form.get("rule");
            if (Strings.isBlank(roleId) || Strings.isBlank(columnId) || Strings.isBlank(rule)) {
                throw new RuntimeException("Parameter missing: roleId, columnId or rule");
            }
            rolePermissionMapService.insertColumnPermission(roleId, columnId, rule);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
