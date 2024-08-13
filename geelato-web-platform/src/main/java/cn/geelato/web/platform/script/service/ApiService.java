package cn.geelato.web.platform.script.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.script.entity.Api;
import cn.geelato.web.platform.script.entity.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class ApiService extends BaseService {

    @Lazy
    @Autowired
    private ApiParamService apiParamService;

    /**
     * 创建一条数据
     *
     * @param model 实体数据
     * @return
     */
    public Api createModel(Api model) {
        // 创建api
        Api api = super.createModel(model);
        // 创建apiParam
        api.setParameters(model.getParameters());
        api.setParameters(apiParamService.batchHandleModelByApis(api));

        return api;
    }

    /**
     * 更新一条数据
     *
     * @param model 实体数据
     * @return
     */
    public Api updateModel(Api model) {
        // 更新api
        Api api = super.updateModel(model);
        // 更新apiParam
        api.setParameters(model.getParameters());
        api.setParameters(apiParamService.batchHandleModelByApis(api));

        return api;
    }

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(Api model) {
        // 删除关联申请
        Map<String, Object> params = new HashMap<>();
        params.put("apiId", model.getId());
        List<ApiParam> list = apiParamService.queryModel(ApiParam.class, params);
        if (list != null && list.size() > 0) {
            for (ApiParam map : list) {
                apiParamService.isDeleteModel(map);
            }
        }
        // 删除
        super.isDeleteModel(model);
    }
}
