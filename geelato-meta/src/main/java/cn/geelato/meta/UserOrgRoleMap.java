package cn.geelato.meta;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户组织角色关系表（用户挂组织角色）。
 * <p>语义对齐安全契约 cn.geelato.security.UserOrgRole：部门角色对用户生效的唯一途径
 * 是经本表直挂（userId × orgId × roleId），orgId 记录角色来源部门；用户可以不是该部门成员
 * （成员身份见 OrgUserMap，与本表独立，不产生角色继承）。
 */
@Getter
@Setter
@Entity(name = "platform_user_r_org_role", catalog = "platform")
@Title(title = "用户组织角色关系表")
public class UserOrgRoleMap extends BaseEntity {
    @Title(title = "用户ID")
    @ForeignKey(fTable = User.class)
    @Col(name = "user_id", refTables = "platform_user", refColName = "platform_user.id")
    private String userId;
    @Title(title = "用户名称")
    @Col(name = "user_name", isRefColumn = true, refLocalCol = "userId", refColName = "platform_user.name")
    private String userName;
    @Title(title = "来源组织ID（角色挂靠的部门）")
    @ForeignKey(fTable = Org.class)
    @Col(name = "org_id", refTables = "platform_org", refColName = "platform_org.id")
    private String orgId;
    @Title(title = "来源组织名称")
    @Col(name = "org_name", isRefColumn = true, refLocalCol = "orgId", refColName = "platform_org.name")
    private String orgName;
    @Title(title = "角色ID")
    @ForeignKey(fTable = Role.class)
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    private String roleId;
    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    private String roleName;
}
