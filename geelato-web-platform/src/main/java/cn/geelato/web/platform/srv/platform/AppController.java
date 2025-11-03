package cn.geelato.web.platform.srv.platform;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.User;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.App;
import cn.geelato.web.platform.srv.platform.service.AppService;
import cn.geelato.web.platform.srv.security.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController("/app")
@Slf4j
public class AppController extends BaseController {
    private static final Class<App> CLAZZ = App.class;

    private final AppService appService;
    private final UserService userService;


    @Autowired
    public AppController(AppService appService, UserService userService) {
        this.appService = appService;
        this.userService = userService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody, DEFAULT_ORDER_BY);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return appService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<App>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(DEFAULT_ORDER_BY);
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(appService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryByUser", method = RequestMethod.GET)
    public ApiResult queryByUser(String tenantCode, String userId) {
        try {
            int userType = 0;
            if (Strings.isBlank(userId)) {
                cn.geelato.security.User user = SessionCtx.getCurrentUser();
                userType = user.getType();
                userId = user.getUserId();
            } else {
                User user = userService.getModel(User.class, userId);
                userType = user.getType();
            }
            if (Strings.isBlank(tenantCode) || Strings.isBlank(userId)) {
                return ApiResult.fail("The tenant code and user ID cannot be empty");
            }
            Map<String, Object> map = new HashMap<>();
            map.put("tenantCode", tenantCode);
            map.put("userId", userId);
            List<Map<String, Object>> appList = dao.queryForMapList("query_app_by_role_user", map);
            // app.type normal：普通应用；platform：平台应用
            // app.purpose inside：企业内部； outside：企业外部； side：企业内外部
            // user.type 0：员工；1：系统；2：企业外部人员；999：租户管理员
            if (appList != null && !appList.isEmpty()) {
                if (userType == 0) {
                    // user.type: [0],只能看 app.type=normal,app.purpose=inside/side
                    appList = appList.stream().filter(x -> "normal".equals(x.get("type")) && ("inside".equals(x.get("purpose")) || "side".equals(x.get("purpose")))).collect(Collectors.toList());
                } else if (userType == 1 || userType == 999) {
                    // user.type: [1,999],只能看 app.type=normal/platform,app.purpose=inside/outside/side
                    appList = appList.stream().filter(x -> "normal".equals(x.get("type")) || "platform".equals(x.get("type"))).collect(Collectors.toList());
                } else if (userType == 2) {
                    // user.type: [2], 只能看 app.type=normal,app.purpose=outside
                    appList = appList.stream().filter(x -> "normal".equals(x.get("type")) && "outside".equals(x.get("purpose"))).collect(Collectors.toList());
                } else {
                    appList = new ArrayList<>();
                }
            }
            return ApiResult.success(appList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(appService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<App> createOrUpdate(@RequestBody App form, String useType) {
        try {
            if ("import".equalsIgnoreCase(useType)) {
                if (Strings.isBlank(form.getId())) {
                    throw new RuntimeException("The ID cannot be empty");
                }
                return ApiResult.success(appService.importModel(form));
            }
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(appService.updateModel(form));
            } else {
                return ApiResult.success(appService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate/{type}", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@PathVariable(required = true) String type, @RequestBody App form) {
        try {
            if ("code".equalsIgnoreCase(type)) {
                Map<String, String> params = new HashMap<>();
                params.put("code", form.getCode());
                params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
                params.put("tenant_code", form.getTenantCode());
                return ApiResult.success(appService.validate("platform_app", form.getId(), params));
            } else if ("id".equalsIgnoreCase(type) && Strings.isNotBlank(form.getId())) {
                Map<String, Object> params = new HashMap<>();
                params.put("id", form.getId());
                List<App> apps = appService.queryModel(CLAZZ, params);
                return ApiResult.success(apps == null || apps.isEmpty());
            } else {
                throw new RuntimeException("type is error");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryPermissionByPage", method = RequestMethod.GET)
    public ApiResult<List<Map<String, Object>>> queryPermissionByPage() {
        try {
            Map<String, Object> params = this.getQueryParameters();
            List<Map<String, Object>> queryList = dao.queryForMapList("platform_permission_by_app_page", params);
            return ApiResult.success(queryList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryRolePermissionByPage", method = RequestMethod.GET)
    public ApiResult<List<Map<String, Object>>> queryRolePermissionByPage() {
        try {
            Map<String, Object> params = this.getQueryParameters();
            List<Map<String, Object>> queryList = dao.queryForMapList("platform_role_r_permission_by_app_page", params);
            return ApiResult.success(queryList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/upload/{fileId}", method = RequestMethod.POST)
    public ApiResult<?> upload(@PathVariable(required = true) String fileId) throws IOException {
        try {
            return ApiResult.success(appService.updateAppVersion(fileId));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
