package cn.geelato.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于平台角色表的角色快照加载器：platform_role（角色实体）+
 * platform_org_r_role（组织挂靠角色，部门下的角色）。
 *
 * <p>与 {@link DBUserSnapshotLoader} 同层（业务层 geelato-web-platform，保留
 * cn.geelato.security 包名维持 split package 现状），框架层仅保留
 * {@link RoleSnapshotLoader} SPI 接口。</p>
 */
public class DBRoleSnapshotLoader implements RoleSnapshotLoader {

    private static final String SQL_ROLES =
            "SELECT id, code, name, type, tenant_code FROM platform_role WHERE del_status = 0";

    private static final String SQL_ORG_ROLES =
            "SELECT org_id, role_id FROM platform_org_r_role WHERE del_status = 0";

    private final JdbcTemplate jdbcTemplate;

    public DBRoleSnapshotLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RoleSnapshot load() {
        Map<String, Role> roleById = new LinkedHashMap<>();
        List<Role> roles = jdbcTemplate.query(SQL_ROLES, (rs, rowNum) -> {
            Role role = new Role();
            role.setId(trimToNull(rs.getString("id")));
            role.setCode(trimToNull(rs.getString("code")));
            role.setName(trimToNull(rs.getString("name")));
            role.setType(trimToNull(rs.getString("type")));
            role.setTenantCode(trimToNull(rs.getString("tenant_code")));
            return role;
        });
        for (Role role : roles) {
            if (role.getId() != null) {
                roleById.put(role.getId(), role);
            }
        }

        Map<String, List<OrgRole>> orgRolesByOrgId = new LinkedHashMap<>();
        jdbcTemplate.query(SQL_ORG_ROLES, rs -> {
            String orgId = trimToNull(rs.getString("org_id"));
            Role role = roleById.get(trimToNull(rs.getString("role_id")));
            if (orgId == null || role == null) {
                return;
            }
            OrgRole orgRole = new OrgRole();
            copyRole(role, orgRole);
            orgRole.setOrgId(orgId);
            orgRolesByOrgId.computeIfAbsent(orgId, k -> new ArrayList<>()).add(orgRole);
        });

        return RoleSnapshot.from(roleById, orgRolesByOrgId);
    }

    private void copyRole(Role source, Role target) {
        target.setId(source.getId());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setType(source.getType());
        target.setTenantCode(source.getTenantCode());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
