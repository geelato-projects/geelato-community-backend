package cn.geelato.web.platform.srv.security;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.OrgRoleMap;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.service.OrgRoleMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * 组织角色关系管理（角色挂组织）
 */
@ApiRestController(value = "/security/org/role")
@Slf4j
public class OrgRoleMapController extends BaseController {
    private static final Class<OrgRoleMap> CLAZZ = OrgRoleMap.class;
    private final OrgRoleMapService orgRoleMapService;

    @Autowired
    public OrgRoleMapController(OrgRoleMapService orgRoleMapService) {
        this.orgRoleMapService = orgRoleMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return orgRoleMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return orgRoleMapService.pageQueryModel("page_query_platform_org_r_role", params, pageQueryRequest);
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
            return ApiResult.success(orgRoleMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult inserts(@RequestBody OrgRoleMap form) {
        try {
            return ApiResult.success(orgRoleMapService.insertModels(form));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    public ApiResult<NullResult> switchInsert(@RequestBody OrgRoleMap form) {
        try {
            orgRoleMapService.switchModel(form);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            OrgRoleMap model = orgRoleMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            orgRoleMapService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryRoles/{orgId}", method = RequestMethod.GET)
    public ApiResult queryRoleByOrg(@PathVariable(required = true) String orgId, String tenantCode) {
        try {
            return ApiResult.success(orgRoleMapService.queryRoleByOrg(orgId, tenantCode));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
