package cn.geelato.meta;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.meta.User;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by hongxq on 2015/6/17.
 */
@Getter
@Setter
@Entity(name = "platform_role_r_user")
@Title(title = "角色用户关系表")
public class RoleUserMap extends BaseEntity {
    @Title(title = "角色ID")
    @ForeignKey(fTable = Role.class)
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    private String roleId;
    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    private String roleName;
    @Title(title = "用户ID")
    @ForeignKey(fTable = User.class)
    @Col(name = "user_id", refTables = "platform_user", refColName = "platform_user.id")
    private String userId;
    @Title(title = "用户名称")
    @Col(name = "user_name", isRefColumn = true, refLocalCol = "userId", refColName = "platform_user.name")
    private String userName;
}
