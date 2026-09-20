package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户挂组织角色契约模型（userId × orgId × roleId 三元关系，嵌于 User 快照时 userId 隐含）。
 * <p>
 * 语义：部门角色对用户生效的唯一途径——用户经本关系直挂，orgId 记录角色来源部门。
 * 用户可以不是来源部门的成员（成员身份见 UserOrg，与本关系独立，不产生角色继承）。
 */
@Getter
@Setter
public class UserOrgRole extends Role {

    // 角色来源部门（角色挂靠的组织）ID
    private String orgId;
}
