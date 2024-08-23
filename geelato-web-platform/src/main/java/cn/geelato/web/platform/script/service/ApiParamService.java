package cn.geelato.web.platform.script.service;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.script.entity.Api;
import cn.geelato.web.platform.script.entity.ApiParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@Component
public class ApiParamService extends BaseService {

    /**
     * 批量查询
     *
     * @param apiId
     * @param appId
     * @param tenantCode
     * @return
     */
    public List<ApiParam> queryModelsByApi(String apiId, String appId, String tenantCode) {
        Map<String, Object> params = new HashMap<>();
        if (StringUtils.isNotBlank(apiId)) {
            params.put("apiId", apiId);
        }
        if (StringUtils.isNotBlank(appId)) {
            params.put("appId", appId);
        }
        if (StringUtils.isNotBlank(apiId)) {
            params.put("tenantCode", tenantCode);
        }
        return queryModel(ApiParam.class, params);
    }

    public List<ApiParam> buildChildren(List<ApiParam> parentList, List<ApiParam> existList) {
        if (parentList != null && parentList.size() > 0) {
            for (ApiParam apiParam : parentList) {
                List<ApiParam> childList = existList.stream().filter(param -> apiParam.getId().equals(param.getPid())).collect(Collectors.toList());
                if (childList != null && childList.size() > 0) {
                    buildChildren(childList, existList);
                    apiParam.setChildren(childList);
                }
            }
        }

        return parentList;
    }

    public List<ApiParam> queryModelsByApis(String apiId, String appId, String tenantCode) {
        List<ApiParam> existList = this.queryModelsByApi(apiId, appId, tenantCode);
        List<ApiParam> parentList = new ArrayList<>();
        if (existList != null && existList.size() > 0) {
            parentList = existList.stream().filter(param -> StringUtils.isBlank(param.getPid())).collect(Collectors.toList());
            buildChildren(parentList, existList);
        }

        return parentList;
    }

    public List<ApiParam> batchCreateModel(Api api, String pid, List<ApiParam> records) {
        List<ApiParam> result = new ArrayList<>();
        if (records != null && records.size() > 0) {
            for (ApiParam param : records) {
                param.setApiId(api.getId());
                param.setPid(pid);
                param.setAppId(api.getAppId());
                param.setTenantCode(api.getTenantCode());
                param.setId(null);
                ApiParam apiParam = this.createModel(param);
                if (param.getChildren() != null && param.getChildren().size() > 0) {
                    List<ApiParam> apiParams = batchCreateModel(api, apiParam.getId(), param.getChildren());
                    apiParam.setChildren(apiParams);
                }
                result.add(apiParam);
            }
        }

        return result;
    }

    public void isDeleteModels(Api model) {
        List<ApiParam> apiParams = this.queryModelsByApi(model.getId(), null, null);
        if (apiParams != null && apiParams.size() > 0) {
            for (ApiParam param : apiParams) {
                this.isDeleteModel(param);
            }
        }
    }
}
