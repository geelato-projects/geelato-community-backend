package cn.geelato.web.platform.script.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.script.entity.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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
        // 删除接口参数
        apiParamService.isDeleteModels(model);
        // 创建接口参数，请求参数
        api.setRequestParams(apiParamService.batchCreateModel(api, null, model.getRequestParams()));
        // 创建接口参数，请求参数
        api.setResponseParams(apiParamService.batchCreateModel(api, null, model.getResponseParams()));

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
        // 删除接口参数
        apiParamService.isDeleteModels(model);
        // 创建apiParam
        api.setRequestParams(apiParamService.batchCreateModel(api, null, model.getRequestParams()));
        api.setResponseParams(apiParamService.batchCreateModel(api, null, model.getResponseParams()));

        return api;
    }

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(Api model) {
        // 删除接口参数
        apiParamService.isDeleteModels(model);
        // 删除
        super.isDeleteModel(model);
    }
}

