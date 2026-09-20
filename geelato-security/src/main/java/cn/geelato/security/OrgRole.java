package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

/**
 * 组织挂靠角色（角色挂组织）契约模型。
 * <p>
 * 语义：角色挂靠到某个组织（部门）上，由该部门提供。
 * 仅直属部门生效，不沿组织树向上递归。
 * 组织挂靠本身不使部门成员自动获得该角色——部门角色对用户生效的唯一途径
 * 是用户经 用户挂组织角色（{@link UserOrgRole}）直挂。
 */
@Getter
@Setter
public class OrgRole extends Role {

    // 角色挂靠的组织（部门）ID
    private String orgId;
}
