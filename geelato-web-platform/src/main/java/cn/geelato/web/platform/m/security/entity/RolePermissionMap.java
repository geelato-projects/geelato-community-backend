package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.*;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author hongxq
 */
@Getter
@Setter
@Entity(name = "platform_role_r_permission")
@Title(title = "角色权限关系表")
public class RolePermissionMap extends BaseEntity {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;

    @Title(title = "角色ID")
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    @ForeignKey(fTable = Role.class)
    private String roleId;

    @Title(title = "权限ID")
    @Col(name = "permission_id", refTables = "platform_permission", refColName = "platform_permission.id")
    @ForeignKey(fTable = Permission.class)
    private String permissionId;

    @Title(title = "角色所有的权限id")
    @Transient
    private String permissionIds;

    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    private String roleName;

    @Title(title = "权限名称")
    @Col(name = "permission_name")
    private String permissionName;


}
