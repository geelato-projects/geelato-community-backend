package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 * Created by hongxq on 2015/6/17.
 */

@Entity(name = "platform_role_r_user")
@Title(title = "角色用户关系表")
public class RoleUserMap extends BaseEntity {
    private String roleId;
    private String roleName;
    private String userId;
    private String userName;

    @Title(title = "角色ID")
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    @ForeignKey(fTable = Role.class)
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    @Title(title = "用户ID")
    @Col(name = "user_id", refTables = "platform_user", refColName = "platform_user.id")
    @ForeignKey(fTable = User.class)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Title(title = "用户名称")
    @Col(name = "user_name", isRefColumn = true, refLocalCol = "userId", refColName = "platform_user.name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
