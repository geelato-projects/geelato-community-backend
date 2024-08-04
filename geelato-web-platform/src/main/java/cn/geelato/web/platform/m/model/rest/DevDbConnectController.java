package cn.geelato.web.platform.m.model.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.model.service.DevDbConnectService;
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
@RequestMapping(value = "/api/model/connect")
public class DevDbConnectController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<ConnectMeta> CLAZZ = ConnectMeta.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("dbConnectName", "dbName", "dbHostnameIp", "dbSchema"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(DevDbConnectController.class);
    @Autowired
    private DevDbConnectService devDbConnectService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = devDbConnectService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(devDbConnectService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult<>();
        try {
            ConnectMeta meta = devDbConnectService.getModel(CLAZZ, id);
            devDbConnectService.setApps(meta);
            result.setData(meta);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody ConnectMeta form) {
        ApiResult result = new ApiResult<>();
        try {
            if (Strings.isNotBlank(form.getId())) {
                result.setData(devDbConnectService.updateModel(form));
            } else {
                result.setData(devDbConnectService.createModel(form));
            }
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
            ConnectMeta model = devDbConnectService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            devDbConnectService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/jdbcConnect", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult<Boolean> jdbcConnect(@RequestBody ConnectMeta form) {
        ApiResult<Boolean> result = new ApiResult<>();
        try {
            Boolean isConnected = ConnectUtils.connectionTest(form);
            result.setData(isConnected);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }
}
