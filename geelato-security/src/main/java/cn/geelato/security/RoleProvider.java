package cn.geelato.security;

import java.util.Collections;
import java.util.List;

/**
 * 角色信息提供者（与 {@link OrgProvider}/{@link UserProvider} 同范式的快照式读侧）。
 * 面向消费方暴露只读查询能力，具体缓存与加载策略由实现类负责。
 * <p>
 * 覆盖两块数据：角色实体（按 ID / 按 code）与组织挂靠角色（部门下的角色，
 * 对应 platform_org_r_role）——供 {@link SecurityProvider} 的桥接实现
 * （BridgeSecurityProvider）求解部门角色与人员。
 */
public interface RoleProvider {

    /** 按角色ID查询；不存在返回 null。 */
    Role getRole(String roleId);

    /** 按角色编码查询（业务引用角色一律用 code）；不存在返回 null。 */
    Role getRoleByCode(String roleCode);

    /**
     * 组织挂靠的角色（部门下的角色，直属部门，不沿组织树向上递归）。
     * 基数小，不分页。
     */
    default List<OrgRole> getOrgRoles(String orgId) {
        return Collections.emptyList();
    }

    void refresh();
}
