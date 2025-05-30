package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.security.User;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.service.PermissionService;
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
@ApiRestController(value = "/security/permission")
@Slf4j
public class PermissionController extends BaseController {
    private static final Class<Permission> CLAZZ = Permission.class;
    private final PermissionService permissionService;

    @Autowired
    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return permissionService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return ApiResult.success(permissionService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryByUser", method = RequestMethod.GET)
    public ApiResult queryByUser() {
        try {
            Map<String, Object> params = this.getQueryParameters();
            if (params.get("userId") == null) {
                User user = SessionCtx.getCurrentUser();
                params.put("userId", user.getUserId());
            }
            if (params.get("userId") == null) {
                throw new RuntimeException("Parameter missing: userId");
            }
            List<Map<String, Object>> appList = dao.queryForMapList("query_permission_by_role_user", params);
            return ApiResult.success(appList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable() String id) {
        try {
            Permission model = permissionService.getModel(CLAZZ, id);
            model.setPerDefault(permissionService.isDefault(model));
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<?> createOrUpdate(@RequestBody Permission form) {
        try {
            form.afterSet();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(permissionService.updateModel(form));
            } else {
                return ApiResult.success(permissionService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            Permission model = permissionService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            permissionService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Permission form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(permissionService.validate("platform_permission", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/default/{type}/{object}", method = RequestMethod.POST)
    public ApiResult<NullResult> defaultTablePermission(@PathVariable(required = true) String type, @PathVariable(required = true) String object, String parentObject, String appId) {
        try {
            permissionService.resetDefaultPermission(type, object, parentObject, appId);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/shift", method = RequestMethod.POST)
    public ApiResult<Boolean> shift(@RequestBody Map<String, Object> params) {
        try {
            String ids = params.get("ids") == null ? "" : params.get("ids").toString();
            permissionService.shiftPermission(ids);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/getEffective", method = RequestMethod.GET)
    public ApiResult<?> getEffective(String userId, String entity) {
        List<cn.geelato.security.Permission> entityPermission = EnvManager.singleInstance().getUserPermission(userId, entity);
        List<cn.geelato.security.Permission> maxWeightPermissionList = null;
        cn.geelato.security.Permission rtnPermission = null;
        Optional<cn.geelato.security.Permission> maxWeightPermission = entityPermission.stream().max(Comparator.comparing(cn.geelato.security.Permission::getWeight));
        if (maxWeightPermission.isPresent()) {
            int maxWeight = maxWeightPermission.get().getWeight();
            maxWeightPermissionList = entityPermission.stream().filter(x -> x.getWeight() == maxWeight).toList();
        }
        if (maxWeightPermissionList != null) {
            rtnPermission = maxWeightPermissionList.get(0);
        }
        return ApiResult.success(rtnPermission);
    }
}
