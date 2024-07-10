package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.*;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 * Created by hongxq on 2015/6/17.
 */

@Entity(name = "platform_role_r_permission")
@Title(title = "角色权限关系表")
public class RolePermissionMap extends BaseEntity {

    private String appId;
    private String roleId;
    private String permissionId;
    private String permissionIds;
    private String roleName;
    private String permissionName;

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Title(title = "角色ID")
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    @ForeignKey(fTable = Role.class)
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    @Title(title = "权限ID")
    @Col(name = "permission_id", refTables = "platform_permission", refColName = "platform_permission.id")
    @ForeignKey(fTable = Permission.class)
    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    @Title(title = "角色所有的权限id")
    @Transient
    public String getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(String permissionIds) {
        this.permissionIds = permissionIds;
    }

    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @Title(title = "权限名称")
    @Col(name = "permission_name")
    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }
}
