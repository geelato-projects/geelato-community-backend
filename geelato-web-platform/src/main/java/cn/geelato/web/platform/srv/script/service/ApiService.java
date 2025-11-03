package cn.geelato.web.platform.srv.script.service;

import cn.geelato.web.platform.srv.platform.service.BaseService;
import cn.geelato.meta.Api;
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
     * <p>
     * 根据提供的实体数据，创建一条新的API记录，并处理相关的接口参数。
     *
     * @param model 实体数据对象，包含要创建的API的详细信息
     * @return 返回新创建的API对象
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
     * <p>
     * 更新指定的实体数据，并同步更新相关接口参数。
     *
     * @param model 实体数据对象，包含需要更新的数据
     * @return 返回更新后的实体数据对象
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
     * <p>
     * 对指定的API对象进行逻辑删除操作。
     *
     * @param model 要进行逻辑删除的API对象
     */
    public void isDeleteModel(Api model) {
        // 删除接口参数
        apiParamService.isDeleteModels(model);
        // 删除
        super.isDeleteModel(model);
    }
}

