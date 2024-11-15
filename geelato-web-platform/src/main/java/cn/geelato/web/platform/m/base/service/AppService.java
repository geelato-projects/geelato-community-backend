package cn.geelato.web.platform.m.base.service;

import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.security.entity.RoleAppMap;
import cn.geelato.web.platform.m.security.service.RoleAppMapService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class AppService extends BaseSortableService {
    @Lazy
    @Autowired
    private RoleAppMapService roleAppMapService;

    /**
     * 逻辑删除方法
     * <p>
     * 该方法首先执行应用的删除操作，然后删除与角色相关联的应用信息。
     *
     * @param model 要删除的应用模型对象
     */
    public void isDeleteModel(App model) {
        // 应用删除
        super.isDeleteModel(model);
        // 角色关联应用信息删除
        Map<String, Object> params = new HashMap<>();
        params.put("appId", model.getId());
        List<RoleAppMap> rList = roleAppMapService.queryModel(RoleAppMap.class, params);
        if (rList != null) {
            for (RoleAppMap oModel : rList) {
                roleAppMapService.isDeleteModel(oModel);
            }
        }
    }

    /**
     * 创建应用
     * <p>
     * 该方法用于创建一个新的应用，并关联平台级角色。
     * <p>
     * 1. 创建应用（app）。
     * 2. 创建应用与角色的关联关系（role_r_app）。
     *
     * @param model 包含应用信息的模型对象
     * @return 返回创建的应用对象
     */
    public App createModel(App model) {
        // 创建应用
        App app = super.createModel(model);
        // 关联平台级角色
        if (Strings.isNotBlank(model.getRoles())) {
            RoleAppMap map = new RoleAppMap();
            map.setAppId(app.getId());
            map.setRoleId(model.getRoles());
            roleAppMapService.insertModels(map);
        }

        return app;
    }

    /**
     * 导入应用
     * <p>
     * 该方法用于导入一个应用，主要步骤包括：
     * 1. 创建应用实例。
     * 2. 变更应用ID。
     * 3. 创建角色与应用关联记录。
     *
     * @param model 包含应用信息的模型对象
     * @return 导入后的应用实例
     */
    public App importModel(App model) {
        String id = model.getId();
        // 创建应用
        model.setId(null);
        App app = super.createModel(model);
        // 更新id
        dao.getJdbcTemplate().update("UPDATE platform_app SET id=? WHERE id=?", id, app.getId());
        app.setId(id);
        // 关联平台级角色
        if (Strings.isNotBlank(model.getRoles())) {
            RoleAppMap map = new RoleAppMap();
            map.setAppId(app.getId());
            map.setRoleId(model.getRoles());
            roleAppMapService.insertModels(map);
        }

        return app;
    }

    /**
     * 更新应用信息
     * <p>
     * 该方法负责更新应用的信息，包括应用本身的信息以及与角色关联的应用名称。
     * 1. 更新应用的基本信息。
     * 2. 如果应用名称发生变化，则更新role_r_app表中对应的应用名称。
     *
     * @param model 包含要更新的应用信息的App对象
     * @return 更新后的App对象
     */
    public App updateModel(App model) {
        // 查询应用
        App oldModel = this.getModel(App.class, model.getId());
        Assert.notNull(oldModel, ApiErrorMsg.IS_NULL);
        // 更新应用
        App app = super.updateModel(model);
        // 更新应用名称
        if (!oldModel.getName().equals(model.getName())) {
            List<RoleAppMap> roleAppMaps = roleAppMapService.queryModelByIds(null, model.getId());
            for (RoleAppMap roleAppMap : roleAppMaps) {
                roleAppMap.setAppName(model.getName());
                roleAppMapService.updateModel(roleAppMap);
            }
        }

        return app;
    }
}
