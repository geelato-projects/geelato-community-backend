package cn.geelato.web.platform.srv.security;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.RoleUserMap;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.service.RoleUserMapService;
import cn.geelato.web.platform.utils.CacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController(value = "/security/role/user")
@Slf4j
public class RoleUserMapController extends BaseController {
    private static final Class<RoleUserMap> CLAZZ = RoleUserMap.class;
    private final RoleUserMapService roleUserMapService;

    @Autowired
    public RoleUserMapController(RoleUserMapService roleUserMapService) {
        this.roleUserMapService = roleUserMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return roleUserMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return roleUserMapService.pageQueryModel("page_query_platform_role_r_user", params, pageQueryRequest);
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
            return ApiResult.success(roleUserMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult inserts(@RequestBody RoleUserMap form) {
        try {
            List<RoleUserMap> models = roleUserMapService.insertModels(form);
            removeCache(form.getUserId());
            return ApiResult.success(models);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    public ApiResult<NullResult> switchInsert(@RequestBody RoleUserMap form) {
        try {
            roleUserMapService.switchModel(form);
            removeCache(form.getUserId());
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            RoleUserMap model = roleUserMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            roleUserMapService.isDeleteModel(model);
            removeCache(model.getUserId());
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/queryRoles/{userId}", method = RequestMethod.GET)
    public ApiResult queryRoleByUser(@PathVariable(required = true) String userId, String appId, String tenantCode) {
        try {
            return ApiResult.success(roleUserMapService.queryRoleByUser(userId, appId, tenantCode));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private void removeCache(String userIds) {
        if (StringUtils.isBlank(userIds)) {
            return;
        }
        List<String> userList = StringUtils.toListDr(userIds);
        if (userList.isEmpty()) {
            return;
        }
        for (String userId : userList) {
            String patternKey = String.format("*_%s", userId);
            CacheUtil.removeByPattern(patternKey);
        }
    }
}
