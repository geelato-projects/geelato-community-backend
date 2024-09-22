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
     * 逻辑删除
     * 1, 删除 app
     * 2, 删除 role_r_app
     *
     * @param model
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
     * 1，创建 app
     * 2，创建 role_r_app
     *
     * @param model
     * @return
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
     * 1，创建 app
     * 2，变更 app-id
     * 3，创建 role_r_app
     *
     * @param model
     * @return
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
     * 更新应用
     * 1, 更新 app
     * 2, 更新 role_r_app-appName
     *
     * @param model
     * @return
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
