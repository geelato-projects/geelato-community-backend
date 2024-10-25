package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.m.security.entity.Role;
import cn.geelato.web.platform.m.security.entity.RoleAppMap;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.service.AppService;
import cn.geelato.web.platform.m.base.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 */
@Component
public class RoleAppMapService extends BaseService {
    @Autowired
    private RoleService roleService;
    @Autowired
    private AppService appService;

    /**
     * 根据角色ID和应用ID查询角色-应用映射关系列表
     *
     * @param roleId 角色ID
     * @param appId  应用ID
     * @return 角色-应用映射关系列表
     */
    public List<RoleAppMap> queryModelByIds(String roleId, String appId) {
        List<RoleAppMap> list = new ArrayList<>();
        if (Strings.isNotBlank(roleId) && Strings.isNotBlank(appId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("roleId", FilterGroup.Operator.in, roleId);
            filter.addFilter("appId", FilterGroup.Operator.in, appId);
            list = this.queryModel(RoleAppMap.class, filter);
        }

        return list;
    }

    /**
     * 批量添加角色-应用映射关系
     *
     * @param model 角色-应用映射关系对象
     * @return 插入的角色-应用映射关系列表
     * @throws RuntimeException 当角色或应用信息为空时抛出异常
     */
    public List<RoleAppMap> insertModels(RoleAppMap model) {
        // 角色存在，
        List<Role> roles = roleService.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 应用
        List<App> apps = appService.getModelsById(App.class, model.getAppId());
        if (apps == null || apps.isEmpty()) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色-应用
        List<RoleAppMap> maps = this.queryModelByIds(model.getRoleId(), model.getAppId());
        // 对比插入
        List<RoleAppMap> list = new ArrayList<>();
        for (Role role : roles) {
            for (App app : apps) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (RoleAppMap map : maps) {
                        if (role.getId().equals(map.getRoleId()) && app.getId().equals(map.getAppId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    RoleAppMap map = new RoleAppMap();
                    map.setRoleId(role.getId());
                    map.setRoleName(role.getName());
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
