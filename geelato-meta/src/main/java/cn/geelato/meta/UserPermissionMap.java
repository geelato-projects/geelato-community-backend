package cn.geelato.meta;


import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_user_r_permission")
@Title(title = "用户权限关系表")
public class UserPermissionMap extends BaseEntity {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;

    @Title(title = "角色ID")
    @Col(name = "user_id", refTables = "platform_user", refColName = "platform_user.id")
    @ForeignKey(fTable = User.class)
    private String userId;

    @Title(title = "权限ID")
    @Col(name = "permission_id", refTables = "platform_permission", refColName = "platform_permission.id")
    @ForeignKey(fTable = Permission.class)
    private String permissionId;

    @Title(title = "角色所有的权限id")
    @Transient
    private String permissionIds;

    @Title(title = "角色名称")
    @Col(name = "user_name", isRefColumn = true, refLocalCol = "userId", refColName = "platform_user.name")
    private String roleName;

    @Title(title = "权限名称")
    @Col(name = "permission_name")
    private String permissionName;


}
