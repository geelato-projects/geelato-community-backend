package cn.geelato.meta;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织角色关系表（角色挂组织）。
 * <p>语义对齐安全契约 cn.geelato.security.OrgRole：角色挂靠到直属部门，仅直属部门生效，
 * 不沿组织树向上递归；组织挂靠本身不使部门成员自动获得角色，
 * 成员获得部门角色须经 UserOrgRoleMap 直挂。
 */
@Getter
@Setter
@Entity(name = "platform_org_r_role", catalog = "platform")
@Title(title = "组织角色关系表")
public class OrgRoleMap extends BaseEntity {
    @Title(title = "组织ID")
    @ForeignKey(fTable = Org.class)
    @Col(name = "org_id", refTables = "platform_org", refColName = "platform_org.id")
    private String orgId;
    @Title(title = "组织名称")
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
