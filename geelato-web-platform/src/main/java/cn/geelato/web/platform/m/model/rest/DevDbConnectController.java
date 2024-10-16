package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevDbConnectService;
import jakarta.servlet.http.HttpServletRequest;
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
@ApiRestController("/model/connect")
@Slf4j
public class DevDbConnectController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<ConnectMeta> CLAZZ = ConnectMeta.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("dbConnectName", "dbName", "dbHostnameIp", "dbSchema"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final DevDbConnectService devDbConnectService;

    @Autowired
    public DevDbConnectController(DevDbConnectService devDbConnectService) {
        this.devDbConnectService = devDbConnectService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return devDbConnectService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return ApiResult.success(devDbConnectService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(devDbConnectService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody ConnectMeta form) {
        try {
            // 判断是否存在
            devDbConnectService.isExist(form);
            // 判断是否是更新
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(devDbConnectService.updateModel(form));
            } else {
                return ApiResult.success(devDbConnectService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            ConnectMeta model = devDbConnectService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            devDbConnectService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/jdbcConnect", method = RequestMethod.POST)
    public ApiResult<Boolean> jdbcConnect(@RequestBody ConnectMeta form) {
        try {
            Boolean isConnected = ConnectUtils.connectionTest(form);
            return ApiResult.success(isConnected);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/batchCreate", method = RequestMethod.POST)
    public ApiResult<NullResult> batchCreate(@RequestBody Map<String, Object> params) {
        try {
            String appId = (String) params.get("appId");
            List<String> connectIds = (List<String>) params.get("connectIds");
            String userName = (String) params.get("userName");
            String password = (String) params.get("password");
            if (Strings.isBlank(appId) || connectIds == null || connectIds.isEmpty()) {
                return ApiResult.fail("AppId or connectIds can not be null");
            }
            if (Strings.isBlank(userName) || Strings.isBlank(password)) {
                return ApiResult.fail("UserName or Password can not be null");
            }
            devDbConnectService.batchCreate(appId, connectIds, userName, password);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
