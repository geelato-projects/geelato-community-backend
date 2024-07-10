package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.entity.RolePermissionMap;
import cn.geelato.web.platform.m.security.service.RolePermissionMapService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiPagedResult;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.rest.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Controller
@RequestMapping(value = "/api/security/role/permission")
public class RolePermissionMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<RolePermissionMap> CLAZZ = RolePermissionMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("roleName", "permissionName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(RolePermissionMapController.class);
    @Autowired
    private RolePermissionMapService rolePermissionMapService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = rolePermissionMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQueryOf(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(req);
            result = rolePermissionMapService.pageQueryModel("page_query_platform_role_r_permission", params, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(rolePermissionMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insert(@RequestBody RolePermissionMap form) {
        ApiResult result = new ApiResult();
        try {
            result.setData(rolePermissionMapService.insertModels(form));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult switchModel(@RequestBody RolePermissionMap form) {
        ApiResult result = new ApiResult();
        try {
            rolePermissionMapService.switchModel(form);
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
            RolePermissionMap model = rolePermissionMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            rolePermissionMapService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryTable/{type}/{object}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult queryTablePermissions(@PathVariable(required = true) String type, @PathVariable(required = true) String object, String appId, String tenantCode) {
        ApiResult result = new ApiResult();
        try {
            result.success().setData(rolePermissionMapService.queryTablePermissions(type, object, appId, tenantCode));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryColumn/{type}/{object}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult queryColumnPermissions(@PathVariable(required = true) String type, @PathVariable(required = true) String object, String appId, String tenantCode) {
        ApiResult result = new ApiResult();
        try {
            result.success().setData(rolePermissionMapService.queryColumnPermissions(type, object, appId, tenantCode));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insertTable", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insertTablePermission(@RequestBody RolePermissionMap form) {
        ApiResult result = new ApiResult();
        try {
            rolePermissionMapService.insertTablePermission(form);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insertTable/view", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insertTableViewPermission(@RequestBody RolePermissionMap form) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(form.getPermissionId())) {
                Permission permission = rolePermissionMapService.getModel(Permission.class, form.getPermissionId());
                if (permission != null) {
                    form.setAppId(permission.getAppId());
                }
            }
            rolePermissionMapService.insertTableViewPermission(form);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insertColumn", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insertColumnPermission(@RequestBody Map<String, Object> form) {
        ApiResult result = new ApiResult();
        try {
            String roleId = (String) form.get("roleId");
            String columnId = (String) form.get("columnId");
            String rule = (String) form.get("rule");
            if (Strings.isNotBlank(roleId) && Strings.isNotBlank(columnId) && Strings.isNotBlank(rule)) {
                rolePermissionMapService.insertColumnPermission(roleId, columnId, rule);
            } else {
                result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }
}
