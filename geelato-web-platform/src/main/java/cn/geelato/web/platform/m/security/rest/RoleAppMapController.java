package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.RoleAppMap;
import cn.geelato.web.platform.m.security.service.RoleAppMapService;
import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
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
@RequestMapping(value = "/api/security/role/app")
public class RoleAppMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<RoleAppMap> CLAZZ = RoleAppMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("appName", "roleName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(RoleAppMapController.class);
    @Autowired
    private RoleAppMapService roleAppMapService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = roleAppMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            result = roleAppMapService.pageQueryModel("page_query_platform_role_r_app", params, pageQueryRequest);
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
            result.setData(roleAppMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insert(@RequestBody RoleAppMap form) {
        ApiResult result = new ApiResult();
        try {
            result.setData(roleAppMapService.insertModels(form));
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
            RoleAppMap model = roleAppMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            roleAppMapService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{appId}/{roleId}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult isDeletes(@PathVariable(required = true) String appId, @PathVariable(required = true) String roleId) {
        ApiResult result = new ApiResult();
        try {
            List<RoleAppMap> maps = roleAppMapService.queryModelByIds(roleId, appId);
            if (maps != null && maps.size() > 0) {
                for (RoleAppMap map : maps) {
                    roleAppMapService.isDeleteModel(map);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }
}
