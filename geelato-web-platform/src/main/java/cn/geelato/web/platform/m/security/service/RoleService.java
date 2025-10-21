package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.base.entity.App;
import cn.geelato.web.platform.m.base.service.AppService;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.RoleTypeEnum;
import org.apache.logging.log4j.util.Strings;
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
public class RoleService extends BaseSortableService {
    @Lazy
    @Autowired
    private RoleAppMapService roleAppMapService;
    @Lazy
    @Autowired
    private RolePermissionMapService rolePermissionMapService;
    @Lazy
    @Autowired
    private RoleTreeNodeMapService roleTreeNodeMapService;
    @Lazy
    @Autowired
    private RoleUserMapService roleUserMapService;
    @Lazy
    @Autowired
    private PermissionService permissionService;
    @Lazy
    @Autowired
    private AppService appService;


    /**
     * 获取单条角色信息
     * <p>
     * 根据提供的角色ID，获取对应的角色信息。
     *
     * @param id 角色ID
     * @return 返回对应的角色对象，包含角色信息
     */
    public Role getModel(String id) {
        // 获取
        Role role = super.getModel(Role.class, id);
        // 处理
        if (Strings.isNotBlank(role.getAppId())) {
            App app = appService.getModel(App.class, role.getAppId());
            if (app != null) {
                role.setAppName(app.getName());
            }
        }
        return role;
    }

    /**
     * 逻辑删除角色
     * <p>
     * 删除指定的角色，并同时删除与之关联的角色APP关系、角色权限关系、角色菜单关系和角色用户关系。
     *
     * @param model 要删除的角色对象
     */
    public void isDeleteModel(Role model) {
        // 组织删除
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        super.isDeleteModel(model);
        // 角色APP关系表
        Map<String, Object> params = new HashMap<>();
        params.put("roleId", model.getId());
        List<RoleAppMap> aList = roleAppMapService.queryModel(RoleAppMap.class, params);
        if (aList != null) {
            for (RoleAppMap rModel : aList) {
                roleAppMapService.isDeleteModel(rModel);
            }
        }
        // 角色权限关系表
        List<RolePermissionMap> pList = rolePermissionMapService.queryModel(RolePermissionMap.class, params);
        if (aList != null) {
            for (RolePermissionMap rModel : pList) {
                rolePermissionMapService.isDeleteModel(rModel);
            }
        }
        // 角色菜单关系表
        List<RoleTreeNodeMap> tList = roleTreeNodeMapService.queryModel(RoleTreeNodeMap.class, params);
        if (aList != null) {
            for (RoleTreeNodeMap rModel : tList) {
                roleTreeNodeMapService.isDeleteModel(rModel);
            }
        }
        // 角色用户关系表
        List<RoleUserMap> uList = roleUserMapService.queryModel(RoleUserMap.class, params);
        if (uList != null) {
            for (RoleUserMap rModel : uList) {
                roleUserMapService.isDeleteModel(rModel);
            }
        }
    }

    /**
     * 更新角色信息
     * <p>
     * 根据提供的角色表单对象更新角色信息，并处理与之关联的表。
     *
     * @param form 角色表单对象，包含要更新的角色信息
     * @return 返回更新后的角色对象
     */
    public Role updateModel(Role form) {
        // 原来的数据
        Role model = getModel(Role.class, form.getId());
        // 更新
        Role formMap = super.updateModel(form);
        // 是否用于应用
        if (RoleTypeEnum.PLATFORM.getValue().equals(model.getType()) && !model.isUsedApp()) {
            // 角色APP关系表
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", model.getId());
            List<RoleAppMap> aList = roleAppMapService.queryModel(RoleAppMap.class, params);
            if (aList != null) {
                for (RoleAppMap rModel : aList) {
                    roleAppMapService.isDeleteModel(rModel);
                }
            }
        }
        // 关联表修改
        if (Strings.isNotBlank(form.getName()) && !form.getName().equals(model.getName())) {
            // 角色APP关系表
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", model.getId());
            List<RoleAppMap> aList = roleAppMapService.queryModel(RoleAppMap.class, params);
            if (aList != null) {
                for (RoleAppMap rModel : aList) {
                    rModel.setRoleName(form.getName());
                    roleAppMapService.updateModel(rModel);
                }
            }
            // 角色权限关系表
            List<RolePermissionMap> pList = rolePermissionMapService.queryModel(RolePermissionMap.class, params);
            if (pList != null) {
                for (RolePermissionMap rModel : pList) {
                    rModel.setRoleName(form.getName());
                    rolePermissionMapService.updateModel(rModel);
                }
            }
            // 角色菜单关系表
            List<RoleTreeNodeMap> tList = roleTreeNodeMapService.queryModel(RoleTreeNodeMap.class, params);
            if (tList != null) {
                for (RoleTreeNodeMap rModel : tList) {
                    rModel.setRoleName(form.getName());
                    roleTreeNodeMapService.updateModel(rModel);
                }
            }
            // 角色用户关系表
            List<RoleUserMap> uList = roleUserMapService.queryModel(RoleUserMap.class, params);
            if (uList != null) {
                for (RoleUserMap rModel : uList) {
                    rModel.setRoleName(form.getName());
                    roleUserMapService.updateModel(rModel);
                }
            }
        }

        return formMap;
    }

    /**
     * 创建角色
     * <p>
     * 根据提供的角色对象创建新的角色，并处理与之关联的平台级角色。
     *
     * @param model 角色对象，包含角色的各种信息
     * @return 返回创建的角色对象
     */
    public Role createModel(Role model) {
        // 创建
        Role role = super.createModel(model);
        // 关联平台级角色
        RoleAppMap map = new RoleAppMap();
        map.setRoleId(role.getId());
        if (Strings.isNotBlank(model.getAppIds())) {
            map.setAppId(model.getAppIds());
            roleAppMapService.insertModels(map);
        } else if (Strings.isNotBlank(model.getAppId())) {
            map.setAppId(model.getAppId());
            roleAppMapService.insertModels(map);
        }

        return role;
    }

    /**
     * 查询平台级和应用级角色
     * <p>
     * 根据提供的参数查询平台级和应用级角色列表。
     *
     * @param params 查询参数，包含租户代码和应用ID等信息
     * @return 返回查询到的角色列表
     */
    public List<Role> queryRoles(Map<String, Object> params) {
        String orderBy = "name ASC,weight DESC,seq_no ASC";
        List<Role> roles = new ArrayList<>();
        String tenantCode = (String) params.get("tenantCode");
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : getSessionTenantCode();
        params.put("tenantCode", tenantCode);
        String appId = (String) params.get("appId");
        if (Strings.isNotBlank(appId)) {
            // 应用与角色关联
            List<RoleAppMap> roleAppMapList = roleAppMapService.queryModel(RoleAppMap.class, params);
            List<String> roleIds = new ArrayList<>();
            if (roleAppMapList != null) {
                for (RoleAppMap map : roleAppMapList) {
                    if (!roleIds.contains(map.getRoleId())) {
                        roleIds.add(map.getRoleId());
                    }
                }
            }
            // 应用级角色
            params.put("type", RoleTypeEnum.APP.getValue());
            List<Role> apps = queryModel(Role.class, params, orderBy);
            roles.addAll(apps);
            // 平台级角色
            FilterGroup roleFilter = new FilterGroup();
            roleFilter.addFilter("type", RoleTypeEnum.PLATFORM.getValue());
            roleFilter.addFilter("usedApp", "1");
            roleFilter.addFilter("id", FilterGroup.Operator.in, String.join(",", roleIds));
            List<Role> platforms = this.queryModel(Role.class, roleFilter, orderBy);
            roles.addAll(platforms);
        } else {
            // params.put("type", RoleTypeEnum.PLATFORM.getValue());
            roles = queryModel(Role.class, params, orderBy);
        }
        // appName
        List<String> appIds = new ArrayList<>();
        if (roles != null && !roles.isEmpty()) {
            for (Role role : roles) {
                if (Strings.isNotBlank(role.getAppId()) && !appIds.contains(role.getAppId())) {
                    appIds.add(role.getAppId());
                }
            }
        }
        List<App> apps = new ArrayList<>();
        if (!appIds.isEmpty()) {
            FilterGroup filter = new FilterGroup().addFilter("id", FilterGroup.Operator.in, Strings.join(appIds, ','));
            apps = queryModel(App.class, filter);
        }
        // 填充
        if (apps != null && !apps.isEmpty()) {
            for (Role role : roles) {
                for (App app : apps) {
                    if (Strings.isNotBlank(app.getId()) && app.getId().equals(role.getAppId())) {
                        role.setAppName(app.getName());
                    }
                }
            }
        }

        return roles;
    }

    /**
     * 设置模型角色和权限
     * <p>
     * 根据提供的角色模型，为其设置相应的权限。
     *
     * @param model 角色模型对象，包含应用ID、租户代码等信息
     */
    public void resetRolePermission(Role model) {
        Map<String, Object> params = new HashMap<>();
        params.put("appId", model.getAppId());
        params.put("tenantCode", model.getTenantCode());
        List<TableMeta> tableMetaList = queryModel(TableMeta.class, params);
        List<String> objects = new ArrayList<>();
        for (TableMeta meta : tableMetaList) {
            objects.add(meta.getEntityName());
        }
        if (objects.size() == 0) {
            return;
        }
        // 拥有的权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, PermissionTypeEnum.getTablePermissions());
        tableFilter.addFilter("object", FilterGroup.Operator.in, String.join(",", objects));
        tableFilter.addFilter("tenantCode", model.getTenantCode());
        List<Permission> curPermissions = permissionService.queryModel(Permission.class, tableFilter);
        List<String> permissionIds = new ArrayList<>();
        for (Permission permission : curPermissions) {
            permissionIds.add(permission.getId());
        }
        if (permissionIds.size() == 0) {
            return;
        }
        // 默认权限
        for (Permission permission : curPermissions) {
            for (String code : PermissionService.PERMISSION_DEFAULT_TO_ROLE) {
                if (permission.getCode().equals(String.format("%s%s", permission.getObject(), code))) {
                    rolePermissionMapService.createByRoleAndPermission(model, permission);
                }
            }
        }
    }
}
