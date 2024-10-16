package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.RoleTreeNodeMap;
import cn.geelato.web.platform.m.security.service.RoleTreeNodeMapService;
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
@ApiRestController(value = "/security/role/tree")
@Slf4j
public class RoleTreeNodeMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<RoleTreeNodeMap> CLAZZ = RoleTreeNodeMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("treeNodeText", "title", "roleName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final RoleTreeNodeMapService roleTreeNodeMapService;

    @Autowired
    public RoleTreeNodeMapController(RoleTreeNodeMapService roleTreeNodeMapService) {
        this.roleTreeNodeMapService = roleTreeNodeMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return roleTreeNodeMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return roleTreeNodeMapService.pageQueryModel("page_query_platform_role_r_tree_node", params, pageQueryRequest);
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
            return ApiResult.success(roleTreeNodeMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult insert(@RequestBody RoleTreeNodeMap form) {
        try {
            return ApiResult.success(roleTreeNodeMapService.insertModels(form));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public ApiResult<NullResult> delete(@RequestBody RoleTreeNodeMap form) {
        try {
            roleTreeNodeMapService.cancelModels(form);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            RoleTreeNodeMap model = roleTreeNodeMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            roleTreeNodeMapService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{roleId}/{treeNodeId}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String roleId, @PathVariable(required = true) String treeNodeId) {
        try {
            if (Strings.isBlank(roleId) || Strings.isBlank(treeNodeId)) {
                throw new RuntimeException("Parameter missing: roleId,treeNodeId");
            }
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", roleId);
            params.put("treeNodeId", treeNodeId);
            List<RoleTreeNodeMap> roleTreeNodeMaps = roleTreeNodeMapService.queryModel(CLAZZ, params);
            if (roleTreeNodeMaps != null && roleTreeNodeMaps.size() > 0) {
                for (RoleTreeNodeMap model : roleTreeNodeMaps) {
                    roleTreeNodeMapService.isDeleteModel(model);
                }
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
