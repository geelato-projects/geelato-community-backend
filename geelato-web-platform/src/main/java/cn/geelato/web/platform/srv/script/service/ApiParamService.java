package cn.geelato.web.platform.srv.script.service;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.meta.Api;
import cn.geelato.meta.ApiParam;
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
     * 根据提供的API ID、应用ID和租户代码，批量查询API参数信息。
     *
     * @param apiId     API ID，用于指定要查询的API
     * @param appId     应用ID，用于指定要查询的应用
     * @param tenantCode 租户代码，用于指定要查询的租户
     * @return 返回包含查询到的API参数信息的列表
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

    public void buildChildren(List<ApiParam> parentList, List<ApiParam> existList) {
        if (parentList != null && !parentList.isEmpty()) {
            for (ApiParam apiParam : parentList) {
                List<ApiParam> childList = existList.stream().filter(param -> apiParam.getId().equals(param.getPid())).collect(Collectors.toList());
                if (!childList.isEmpty()) {
                    buildChildren(childList, existList);
                    apiParam.setChildren(childList);
                }
            }
        }

    }

    public List<ApiParam> queryModelsByApis(String apiId, String appId, String tenantCode) {
        List<ApiParam> existList = this.queryModelsByApi(apiId, appId, tenantCode);
        List<ApiParam> parentList = new ArrayList<>();
        if (existList != null && !existList.isEmpty()) {
            parentList = existList.stream().filter(param -> StringUtils.isBlank(param.getPid())).collect(Collectors.toList());
            buildChildren(parentList, existList);
        }

        return parentList;
    }

    public List<ApiParam> batchCreateModel(Api api, String pid, List<ApiParam> records) {
        List<ApiParam> result = new ArrayList<>();
        if (records != null && !records.isEmpty()) {
            for (ApiParam param : records) {
                param.setApiId(api.getId());
                param.setPid(pid);
                param.setAppId(api.getAppId());
                param.setTenantCode(api.getTenantCode());
                param.setId(null);
                ApiParam apiParam = this.createModel(param);
                if (param.getChildren() != null && !param.getChildren().isEmpty()) {
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
        if (apiParams != null && !apiParams.isEmpty()) {
            for (ApiParam param : apiParams) {
                this.isDeleteModel(param);
            }
        }
    }
}
