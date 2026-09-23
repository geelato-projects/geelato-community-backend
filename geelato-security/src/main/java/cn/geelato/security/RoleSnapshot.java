package cn.geelato.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色快照：角色实体（ID/code 双索引）+ 组织挂靠角色（orgId 索引）。
 * 不可变，经 {@link RoleSnapshotLoader} 整体装载、原子替换。
 */
public final class RoleSnapshot {

    private static final RoleSnapshot EMPTY = new RoleSnapshot(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, Role> roleById;
    private final Map<String, Role> roleByCode;
    private final Map<String, List<OrgRole>> orgRolesByOrgId;

    private RoleSnapshot(Map<String, Role> roleById, Map<String, Role> roleByCode,
                         Map<String, List<OrgRole>> orgRolesByOrgId) {
        this.roleById = roleById;
        this.roleByCode = roleByCode;
        this.orgRolesByOrgId = orgRolesByOrgId;
    }

    static RoleSnapshot empty() {
        return EMPTY;
    }

    public static RoleSnapshot from(Map<String, Role> roleById, Map<String, List<OrgRole>> orgRolesByOrgId) {
        Map<String, Role> byId = new LinkedHashMap<>();
        Map<String, Role> byCode = new LinkedHashMap<>();
        for (Map.Entry<String, Role> entry : roleById.entrySet()) {
            byId.put(entry.getKey(), entry.getValue());
            Role role = entry.getValue();
            if (role != null && role.getCode() != null && !role.getCode().isEmpty()) {
                byCode.putIfAbsent(role.getCode(), role);
            }
        }
        Map<String, List<OrgRole>> byOrgId = new LinkedHashMap<>();
        for (Map.Entry<String, List<OrgRole>> entry : orgRolesByOrgId.entrySet()) {
            byOrgId.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new RoleSnapshot(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byCode),
                Collections.unmodifiableMap(byOrgId));
    }

    Role getRole(String roleId) {
        return roleId == null ? null : roleById.get(roleId);
    }

    Role getRoleByCode(String roleCode) {
        return roleCode == null ? null : roleByCode.get(roleCode);
    }

    List<OrgRole> getOrgRoles(String orgId) {
        List<OrgRole> orgRoles = orgId == null ? null : orgRolesByOrgId.get(orgId);
        return orgRoles == null ? Collections.emptyList() : orgRoles;
    }
}
