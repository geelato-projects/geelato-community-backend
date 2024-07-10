package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.web.platform.m.base.entity.AppConnectMap;
import cn.geelato.web.platform.m.base.service.AppConnectMapService;
import cn.geelato.web.platform.m.base.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 */
@Component
public class DevDbConnectService extends BaseService {
    @Lazy
    @Autowired
    private AppConnectMapService appConnectMapService;

    public void setApps(ConnectMeta model) {
        List<AppConnectMap> appConnectMaps = appConnectMapService.queryModelByIds(model.getId(), null);
        if (appConnectMaps != null) {
            List<String> appIds = new ArrayList<>();
            for (AppConnectMap map : appConnectMaps) {
                if (!appIds.contains(map.getAppId())) {
                    appIds.add(map.getAppId());
                }
            }
            model.setApps(String.join(",", appIds));
        }
    }

    public ConnectMeta updateModel(ConnectMeta model) {
        ConnectMeta meta = super.updateModel(model);
        meta.setApps(model.getApps());
        // 关联应用数据链接
        appConnectMapService.insertModels(meta);

        return meta;
    }

    public ConnectMeta createModel(ConnectMeta model) {
        ConnectMeta meta = super.createModel(model);
        meta.setApps(model.getApps());
        // 关联应用数据链接
        appConnectMapService.insertModels(meta);

        return meta;
    }
}
