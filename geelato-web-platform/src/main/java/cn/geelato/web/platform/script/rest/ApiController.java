package cn.geelato.web.platform.script.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.lang.enums.DeleteStatusEnum;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.script.entity.Api;
import cn.geelato.web.platform.script.entity.ApiParam;
import cn.geelato.web.platform.script.enums.AlternateTypeEnum;
import cn.geelato.web.platform.script.service.ApiParamService;
import cn.geelato.web.platform.script.service.ApiService;
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
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController("/script/api")
@Slf4j
public class ApiController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Api> CLAZZ = Api.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("name", "code", "groupName", "remark"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final ApiService apiService;
    private final ApiParamService apiParamService;

    @Autowired
    public ApiController(ApiService apiService, ApiParamService apiParamService) {
        this.apiService = apiService;
        this.apiParamService = apiParamService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = apiService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(apiService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryGroupName", method = RequestMethod.GET)
    public ApiResult queryGroupName(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            List<Api> list = apiService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            List<String> groupNames = new ArrayList<>();
            if (list != null && list.size() > 0) {
                for (Api api : list) {
                    if (Strings.isNotBlank(api.getGroupName()) && !groupNames.contains(api.getGroupName())) {
                        groupNames.add(api.getGroupName());
                    }
                }
            }
            result.setData(groupNames);
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            Api model = apiService.getModel(CLAZZ, id);
            List<ApiParam> apiParams = apiParamService.queryModelsByApis(id, null, null);
            if (apiParams != null && apiParams.size() > 0) {
                model.setRequestParams(apiParams.stream().filter(apiParam -> AlternateTypeEnum.REQUEST.getValue().equalsIgnoreCase(apiParam.getAlternateType())).collect(Collectors.toList()));
                model.setResponseParams(apiParams.stream().filter(apiParam -> AlternateTypeEnum.RESPONSE.getValue().equalsIgnoreCase(apiParam.getAlternateType())).collect(Collectors.toList()));
            }
            result.setData(model);
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody Api form) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(form.getId())) {
                result.setData(apiService.updateModel(form));
            } else {
                result.setData(apiService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult isDelete(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            Api model = apiService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            apiService.isDeleteModel(model);
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult validate(@RequestBody Api form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            result.setData(apiService.validate("platform_api", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/generateOutside", method = RequestMethod.POST)
    public ApiResult generateOutside(@RequestBody Api form) {
        ApiResult result = new ApiResult();
        try {
            FilterGroup filters = new FilterGroup();
            if (Strings.isNotBlank(form.getId())) {
                filters.addFilter("id", FilterGroup.Operator.neq, form.getId());
            }
            // filters.addFilter("appId", form.getAppId());
            // filters.addFilter("tenantCode", form.getTenantCode());
            List<Api> apis = apiService.queryModel(Api.class, filters);
            List<String> outsideUrls = new ArrayList<>();
            if (apis != null) {
                outsideUrls = apis.stream().map(Api::getOutsideUrl).collect(Collectors.toList());
            }
            result.setData(generate(outsideUrls, 10));
        } catch (Exception e) {
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    private String generate(List<String> existList, int limit) {
        String random = "/" + UUIDUtils.generateLowerChars(1) + UUIDUtils.generateNumberAndLowerChars(9);
        if (existList != null && existList.contains(random) && limit > 0) {
            return generate(existList, limit - 1);
        }
        return random;
    }
}
