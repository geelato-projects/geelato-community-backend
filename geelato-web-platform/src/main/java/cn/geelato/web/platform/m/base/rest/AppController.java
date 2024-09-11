package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.Ctx;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.env.entity.User;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.service.AppService;
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
@ApiRestController("/app")
@Slf4j
public class AppController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<App> CLAZZ = App.class;
    private static final String DEFAULT_ORDER_BY = "seq_no ASC,update_at DESC";

    static {
        OPERATORMAP.put("contains", Arrays.asList("name", "code", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final AppService appService;

    @Autowired
    public AppController(AppService appService) {
        this.appService = appService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req, DEFAULT_ORDER_BY);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return appService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<App>> query(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req, DEFAULT_ORDER_BY);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return ApiResult.success(appService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryByUser", method = RequestMethod.GET)
    public ApiResult queryByUser(String tenantCode, String userId) {
        try {
            if (Strings.isBlank(userId)) {
                User user = Ctx.getCurrentUser();
                userId = user != null ? user.getUserId() : "";
            }
            if (Strings.isBlank(tenantCode) || Strings.isBlank(userId)) {
                return ApiResult.fail("The tenant code and user ID cannot be empty");
            }
            Map<String, Object> map = new HashMap<>();
            map.put("tenantCode", tenantCode);
            map.put("userId", userId);
            List<Map<String, Object>> appList = dao.queryForMapList("query_app_by_role_user", map);
            return ApiResult.success(appList);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            App model = appService.getModel(CLAZZ, id);
            appService.setConnects(model);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<App> createOrUpdate(@RequestBody App form) {
        try {
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(appService.updateModel(form));
            } else {
                return ApiResult.success(appService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            App model = appService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            appService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody App form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(appService.validate("platform_app", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryPermissionByPage", method = RequestMethod.GET)
    public ApiResult<List<Map<String, Object>>> queryPermissionByPage(HttpServletRequest req) {
        try {
            Map<String, Object> params = this.getQueryParameters(req);
            List<Map<String, Object>> queryList = dao.queryForMapList("platform_permission_by_app_page", params);
            return ApiResult.success(queryList);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryRolePermissionByPage", method = RequestMethod.GET)
    public ApiResult<List<Map<String, Object>>> queryRolePermissionByPage(HttpServletRequest req) {
        try {
            Map<String, Object> params = this.getQueryParameters(req);
            List<Map<String, Object>> queryList = dao.queryForMapList("platform_role_r_permission_by_app_page", params);
            return ApiResult.success(queryList);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
