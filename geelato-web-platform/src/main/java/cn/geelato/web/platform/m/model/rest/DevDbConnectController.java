package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevDbConnectService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
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
@ApiRestController("/model/connect")
@Slf4j
public class DevDbConnectController extends BaseController {
    private static final Class<ConnectMeta> CLAZZ = ConnectMeta.class;
    private final DevDbConnectService devDbConnectService;

    @Autowired
    public DevDbConnectController(DevDbConnectService devDbConnectService) {
        this.devDbConnectService = devDbConnectService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return devDbConnectService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<?> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(devDbConnectService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable() String id) {
        try {
            return ApiResult.success(devDbConnectService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<?> createOrUpdate(@RequestBody ConnectMeta form) {
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
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable() String id) {
        try {
            ConnectMeta model = devDbConnectService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            devDbConnectService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/jdbcConnect", method = RequestMethod.POST)
    public ApiResult<Boolean> jdbcConnect(@RequestBody ConnectMeta form) {
        try {
            Boolean isConnected = ConnectUtils.connectionTest(form);
            return ApiResult.success(isConnected);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
