package cn.geelato.web.platform.srv.script;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.script.entity.Api;
import cn.geelato.web.platform.srv.script.entity.ApiParam;
import cn.geelato.web.platform.srv.script.enums.AlternateTypeEnum;
import cn.geelato.web.platform.srv.script.service.ApiParamService;
import cn.geelato.web.platform.srv.script.service.ApiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController("/script/api")
@Slf4j
public class ApiController extends BaseController {
    private static final Class<Api> CLAZZ = Api.class;
    private final ApiService apiService;
    private final ApiParamService apiParamService;

    @Autowired
    public ApiController(ApiService apiService, ApiParamService apiParamService) {
        this.apiService = apiService;
        this.apiParamService = apiParamService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return apiService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<Api>> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(apiService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryGroupName", method = RequestMethod.GET)
    public ApiResult<List<String>> queryGroupName() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            List<Api> list = apiService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            List<String> groupNames = new ArrayList<>();
            if (list != null && !list.isEmpty()) {
                for (Api api : list) {
                    if (Strings.isNotBlank(api.getGroupName()) && !groupNames.contains(api.getGroupName())) {
                        groupNames.add(api.getGroupName());
                    }
                }
            }
            return ApiResult.success(groupNames);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<Api> get(@PathVariable(required = true) String id) {
        try {
            Api model = apiService.getModel(CLAZZ, id);
            List<ApiParam> apiParams = apiParamService.queryModelsByApis(id, null, null);
            if (apiParams != null && !apiParams.isEmpty()) {
                model.setRequestParams(apiParams.stream().filter(apiParam -> AlternateTypeEnum.REQUEST.getValue().equalsIgnoreCase(apiParam.getAlternateType())).collect(Collectors.toList()));
                model.setResponseParams(apiParams.stream().filter(apiParam -> AlternateTypeEnum.RESPONSE.getValue().equalsIgnoreCase(apiParam.getAlternateType())).collect(Collectors.toList()));
            }
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<Api> createOrUpdate(@RequestBody Api form) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(apiService.updateModel(form));
            } else {
                return ApiResult.success(apiService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            Api model = apiService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            apiService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Api form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(apiService.validate("platform_api", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generateOutside", method = RequestMethod.POST)
    public ApiResult<String> generateOutside(@RequestBody Api form) {
        try {
            FilterGroup filters = new FilterGroup();
            if (Strings.isNotBlank(form.getId())) {
                filters.addFilter("id", FilterGroup.Operator.neq, form.getId());
            }
            List<Api> apis = apiService.queryModel(Api.class, filters);
            List<String> outsideUrls = new ArrayList<>();
            if (apis != null) {
                outsideUrls = apis.stream().map(Api::getOutsideUrl).collect(Collectors.toList());
            }
            return ApiResult.success(generate(outsideUrls, 10));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private String generate(List<String> existList, int limit) {
        String random = "/" + UUIDUtils.generateLowerChars(1) + UUIDUtils.generateNumberAndLowerChars(9);
        if (existList != null && existList.contains(random) && limit > 0) {
            return generate(existList, limit - 1);
        }
        return random;
    }
}
