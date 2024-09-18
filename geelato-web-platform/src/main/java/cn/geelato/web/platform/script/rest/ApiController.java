package cn.geelato.web.platform.script.rest;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
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
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return apiService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<List<Api>> query(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return ApiResult.success(apiService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryGroupName", method = RequestMethod.GET)
    public ApiResult<List<String>> queryGroupName(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Api form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(apiService.validate("platform_api", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
