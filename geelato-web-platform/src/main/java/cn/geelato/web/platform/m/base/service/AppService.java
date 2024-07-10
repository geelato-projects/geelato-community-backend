package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.security.entity.RoleAppMap;
import cn.geelato.web.platform.m.security.service.RoleAppMapService;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.entity.AppConnectMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    @Lazy
    @Autowired
    private AppConnectMapService appConnectMapService;

    /**
     * 应用拥有的数据链接
     *
     * @param app
     */
    public void setConnects(App app) {
        List<AppConnectMap> appConnectMaps = appConnectMapService.queryModelByIds(null, app.getId());
        if (appConnectMaps != null) {
            List<String> connectIds = new ArrayList<>();
            for (AppConnectMap map : appConnectMaps) {
                if (!connectIds.contains(map.getConnectId())) {
                    connectIds.add(map.getConnectId());
                }
            }
            app.setConnects(String.join(",", connectIds));
        }
    }

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(App model) {
        // 用户删除
        super.isDeleteModel(model);
        //
        Map<String, Object> params = new HashMap<>();
        params.put("appId", model.getId());
        List<RoleAppMap> rList = roleAppMapService.queryModel(RoleAppMap.class, params);
        if (rList != null) {
            for (RoleAppMap oModel : rList) {
                roleAppMapService.isDeleteModel(oModel);
            }
        }
    }

    public App updateModel(App model) {
        App app = super.updateModel(model);
        app.setConnects(model.getConnects());
        // 关联应用数据链接
        appConnectMapService.insertModels(app);

        return app;
    }

    public App createModel(App model) {
        App app = super.createModel(model);
        app.setConnects(model.getConnects());
        // 关联应用数据链接
        appConnectMapService.insertModels(app);
        // 关联平台级角色
        if (Strings.isNotBlank(model.getRoles())) {
            RoleAppMap map = new RoleAppMap();
            map.setAppId(app.getId());
            map.setRoleId(model.getRoles());
            roleAppMapService.insertModels(map);
        }

        return app;
    }
}
