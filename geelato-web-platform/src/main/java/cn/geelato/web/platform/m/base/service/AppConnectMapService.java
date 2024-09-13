package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.entity.AppConnectMap;
import cn.geelato.web.platform.m.model.service.DevDbConnectService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 */
@Component
public class AppConnectMapService extends BaseService {
    @Lazy
    @Autowired
    private DevDbConnectService devDbConnectService;
    @Lazy
    @Autowired
    private AppService appService;


    public List<AppConnectMap> queryModelByIds(String connectId, String appId) {
        List<AppConnectMap> list = new ArrayList<>();
        if (Strings.isNotBlank(connectId) || Strings.isNotBlank(appId)) {
            FilterGroup filter = new FilterGroup();
            if (Strings.isNotBlank(connectId)) {
                filter.addFilter("connectId", FilterGroup.Operator.in, connectId);
            }
            if (Strings.isNotBlank(appId)) {
                filter.addFilter("appId", FilterGroup.Operator.in, appId);
            }
            list = this.queryModel(AppConnectMap.class, filter);
        }

        return list;
    }

    public void insertModels(App app) {
        // 角色存在，
        List<ConnectMeta> connects = devDbConnectService.getModelsById(ConnectMeta.class, null);
        // 角色-应用
        List<AppConnectMap> maps = this.queryModelByIds(null, app.getId());
        // 不存在插入
        List<AppConnectMap> list = new ArrayList<>();
        if (connects != null && connects.size() > 0) {
            for (ConnectMeta meta : connects) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (AppConnectMap map : maps) {
                        if (meta.getId().equals(map.getConnectId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    AppConnectMap map = new AppConnectMap();
                    map.setConnectId(meta.getId());
                    map.setConnectName(meta.getDbConnectName());
                    map.setAppId(app.getId());
                    map.setAppName(app.getName());
                    map = super.createModel(map);
                    list.add(map);
                }
            }
        }
        // 已不存在删除
        if (maps != null && maps.size() > 0) {
            for (AppConnectMap map : maps) {
                boolean isExist = false;
                if (connects != null && connects.size() > 0) {
                    for (ConnectMeta meta : connects) {
                        if (meta.getId().equals(map.getConnectId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    super.isDeleteModel(map);
                }
            }
        }
    }

    public void insertModels(ConnectMeta meta) {
        // 应用
        List<App> apps = appService.getModelsById(App.class, null);
        // 角色-应用
        List<AppConnectMap> maps = this.queryModelByIds(meta.getId(), null);
        // 不存在插入
        if (apps != null && apps.size() > 0) {
            for (App app : apps) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (AppConnectMap map : maps) {
                        if (app.getId().equals(map.getAppId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    AppConnectMap map = new AppConnectMap();
                    map.setConnectId(meta.getId());
                    map.setConnectName(meta.getDbConnectName());
                    map.setAppId(app.getId());
                    map.setAppName(app.getName());
                    super.createModel(map);
                }
            }
        }
        // 已不存在删除
        if (maps != null && maps.size() > 0) {
            for (AppConnectMap map : maps) {
                boolean isExist = false;
                if (apps != null && apps.size() > 0) {
                    for (App app : apps) {
                        if (app.getId().equals(map.getAppId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    super.isDeleteModel(map);
                }
            }
        }
    }


    public List<AppConnectMap> insertModels(AppConnectMap model) {
        // 角色存在，
        List<ConnectMeta> connects = devDbConnectService.getModelsById(ConnectMeta.class, model.getConnectId());
        if (connects == null || connects.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 应用
        List<App> apps = appService.getModelsById(App.class, model.getAppId());
        if (apps == null || apps.isEmpty()) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色-应用
        List<AppConnectMap> maps = this.queryModelByIds(model.getConnectId(), model.getAppId());
        // 对比插入
        List<AppConnectMap> list = new ArrayList<>();
        for (ConnectMeta meta : connects) {
            for (App app : apps) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (AppConnectMap map : maps) {
                        if (meta.getId().equals(map.getConnectId()) && app.getId().equals(map.getAppId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    AppConnectMap map = new AppConnectMap();
                    map.setConnectId(meta.getId());
                    map.setConnectName(meta.getDbConnectName());
                    map.setAppId(app.getId());
                    map.setAppName(app.getName());
                    map = super.createModel(map);
                    list.add(map);
                }
            }
        }

        return list;
    }
}
