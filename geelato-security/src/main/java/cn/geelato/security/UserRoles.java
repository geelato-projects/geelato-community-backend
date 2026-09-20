package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户角色的两类挂靠结果（安全契约读侧）。
 * <p>
 * 区分直挂角色（{@link UserRole}）与组织挂靠角色（{@link UserOrgRole}，含来源部门），
 * 语义见安全契约规范第 3 节。
 */
@Getter
@Setter
public class UserRoles {

    // 用户直挂角色
    private List<UserRole> userRoles;

    // 用户挂组织角色（部门角色，orgId 为角色来源部门）
    private List<UserOrgRole> userOrgRoles;

    /**
     * 生效角色 = 直挂 ∪ 组织挂靠，按角色 code 去重。
     * 与 {@link User#getEffectiveRoles()} 同一语义。
     */
    public List<Role> getEffectiveRoles() {
        List<Role> effectiveRoles = new ArrayList<>();
        if (this.userRoles != null) {
            effectiveRoles.addAll(this.userRoles);
        }
        if (this.userOrgRoles != null) {
            for (UserOrgRole userOrgRole : this.userOrgRoles) {
                boolean duplicated = effectiveRoles.stream()
                        .anyMatch(r -> r.getCode() != null && r.getCode().equals(userOrgRole.getCode()));
                if (!duplicated) {
                    effectiveRoles.add(userOrgRole);
                }
            }
        }
        return effectiveRoles;
    }
}
