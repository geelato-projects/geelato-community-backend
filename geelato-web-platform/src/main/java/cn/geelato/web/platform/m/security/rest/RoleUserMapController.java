package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.RoleUserMap;
import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.security.service.RoleUserMapService;
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
@RequestMapping(value = "/api/security/role/user")
public class RoleUserMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<RoleUserMap> CLAZZ = RoleUserMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("roleName", "userName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(RoleUserMapController.class);
    @Autowired
    private RoleUserMapService roleUserMapService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = roleUserMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            result = roleUserMapService.pageQueryModel("page_query_platform_role_r_user", params, pageQueryRequest);
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
            result.setData(roleUserMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult inserts(@RequestBody RoleUserMap form) {
        ApiResult result = new ApiResult();
        try {
            result.setData(roleUserMapService.insertModels(form));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult switchInsert(@RequestBody RoleUserMap form) {
        ApiResult result = new ApiResult();
        try {
            roleUserMapService.switchModel(form);
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
            RoleUserMap model = roleUserMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            roleUserMapService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }


    @RequestMapping(value = "/queryRoles/{userId}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult queryRoleByUser(@PathVariable(required = true) String userId, String appId, String tenantCode) {
        ApiResult result = new ApiResult();
        try {
            result.setData(roleUserMapService.queryRoleByUser(userId, appId, tenantCode));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }
}
