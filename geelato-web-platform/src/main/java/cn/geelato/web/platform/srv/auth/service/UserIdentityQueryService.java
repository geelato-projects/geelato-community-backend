package cn.geelato.web.platform.srv.auth.service;

import cn.geelato.meta.Role;
import cn.geelato.orm.MetaFactory;
import cn.geelato.security.Tenant;
import cn.geelato.security.UserOrg;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.security.entity.LoginResult;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserIdentityQueryService {
    public List<UserOrg> queryOrgListByUserId(String userId) {
        List<UserOrg> userOrgs = MetaFactory.sql("""
                        SELECT
                            o.id,
                            o.name,
                            oru.default_org AS defaultOrg,
                            o.pid AS pid,
                            o.tenant_code AS tenantCode
                        FROM platform_org_r_user oru
                        LEFT JOIN platform_org o ON oru.org_id = o.id
                        WHERE oru.del_status = 0
                          AND o.status = 1
                          AND o.del_status = 0
                          AND oru.user_id = ?
                        """)
                .param(userId)
                .wrapperResult(this::toUserOrg)
                .list();
        if (userOrgs.isEmpty()) {
            return userOrgs;
        }
        List<String> orgIds = userOrgs.stream()
                .map(UserOrg::getOrgId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        List<Map<String, Object>> mapList = queryOrgTreeInfoByIds(orgIds);
        if (mapList == null || mapList.isEmpty()) {
            return userOrgs;
        }
        Map<String, UserOrg> idToUserOrgMap = mapList.stream().collect(Collectors.toMap(
                map -> map.get("id").toString(),
                map -> new UserOrg(
                        map.get("full_name").toString(),
                        Optional.ofNullable(map.get("dept_id")).map(Object::toString).orElse(null),
                        Optional.ofNullable(map.get("company_id")).map(Object::toString).orElse(null),
                        Optional.ofNullable(map.get("extend_id")).map(Object::toString).orElse(null)
                )
        ));
        userOrgs.forEach(userOrg -> {
            UserOrg matched = idToUserOrgMap.get(userOrg.getOrgId());
            if (matched != null) {
                userOrg.setFullName(matched.getFullName());
                userOrg.setDeptId(matched.getDeptId());
                userOrg.setCompanyId(matched.getCompanyId());
                userOrg.setExtendId(matched.getExtendId());
            }
        });
        return userOrgs;
    }

    public List<Tenant> queryTenantListByUserId(String userId) {
        return queryTenants("""
                SELECT
                    t.`code` AS code,
                    t.company_name AS name
                FROM platform_user u
                LEFT JOIN platform_tenant t ON u.tenant_code = t.`code`
                WHERE t.del_status = 0
                  AND u.id = ?
                """, userId);
    }

    public List<Tenant> queryTenantListByLoginName(String loginName) {
        return queryTenants("""
                SELECT
                    t.`code` AS code,
                    t.company_name AS name
                FROM platform_user u
                LEFT JOIN platform_tenant t ON u.tenant_code = t.`code`
                WHERE t.del_status = 0
                  AND u.login_name = ?
                """, loginName);
    }

    public boolean containsTenant(List<Tenant> tenantList, String tenantCode) {
        return StringUtils.isNotBlank(tenantCode) && tenantList.stream()
                .map(Tenant::getCode)
                .anyMatch(code -> code.equals(tenantCode));
    }

    public boolean containsOrg(List<UserOrg> userOrgList, String orgId) {
        return StringUtils.isNotBlank(orgId) && userOrgList.stream()
                .map(UserOrg::getOrgId)
                .anyMatch(id -> id.equals(orgId));
    }

    public List<Role> getRolesByUserId(String userId, String appId, String tenantCode) {
        List<Map<String, Object>> mapList = queryRoleMapsByUserId(userId, appId, tenantCode);
        return JSON.parseArray(JSON.toJSONString(mapList), Role.class);
    }

    public void populateRoleInfo(LoginResult loginResult, String userId, String appId, String tenantCode) {
        List<Role> roles = getRolesByUserId(userId, appId, tenantCode);
        if (roles == null || roles.isEmpty()) {
            loginResult.setRoleIds(null);
            loginResult.setRoleCodes(null);
            return;
        }
        loginResult.setRoleIds(roles.stream()
                .map(Role::getId)
                .filter(Objects::nonNull)
                .filter(item -> !item.isEmpty())
                .distinct()
                .collect(Collectors.joining(",")));
        loginResult.setRoleCodes(roles.stream()
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .filter(item -> !item.isEmpty())
                .distinct()
                .collect(Collectors.joining(",")));
    }

    private List<Map<String, Object>> queryOrgTreeInfoByIds(List<String> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(orgIds.size(), "?"));
        String sql = """
                WITH RECURSIVE platform_org_tree AS (
                    SELECT
                        o.id,
                        o.pid,
                        o.code,
                        o.name,
                        o.name AS full_name,
                        o.status,
                        o.description,
                        o.extend_id,
                        o.type,
                        o.category,
                        o.tenant_code,
                        o.del_status,
                        o.seq_no,
                        CASE WHEN o.type = 'department' THEN o.id ELSE NULL END AS dept_id,
                        CASE WHEN o.type = 'company' THEN o.id ELSE NULL END AS company_id,
                        CASE WHEN o.type = 'company' THEN o.name ELSE NULL END AS company_name,
                        CASE WHEN o.type = 'company' THEN o.extend_id ELSE NULL END AS company_extend_id
                    FROM platform_org o
                    WHERE o.pid IS NULL AND o.status = 1 AND o.del_status = 0
                    UNION ALL
                    SELECT
                        o.id,
                        o.pid,
                        o.code,
                        o.name,
                        CONCAT(ot.full_name, '/', o.name) AS full_name,
                        o.status,
                        o.description,
                        o.extend_id,
                        o.type,
                        o.category,
                        o.tenant_code,
                        o.del_status,
                        o.seq_no,
                        CASE WHEN o.type = 'department' THEN o.id ELSE ot.dept_id END AS dept_id,
                        CASE WHEN o.type = 'company' THEN o.id ELSE ot.company_id END AS company_id,
                        CASE WHEN o.type = 'company' THEN o.name ELSE ot.company_name END AS company_name,
                        COALESCE(CASE WHEN o.type = 'company' THEN o.extend_id END, ot.company_extend_id) AS company_extend_id
                    FROM platform_org o
                    JOIN platform_org_tree ot ON o.pid = ot.id
                    WHERE o.status = 1 AND o.del_status = 0
                )
                SELECT
                    id,
                    full_name,
                    dept_id,
                    company_id,
                    company_extend_id AS extend_id
                FROM platform_org_tree
                WHERE status = 1
                  AND del_status = 0
                  AND id IN (%s)
                ORDER BY seq_no ASC, full_name ASC
                """.formatted(placeholders);
        return MetaFactory.sql(sql).params(orgIds.toArray()).list();
    }

    private List<Map<String, Object>> queryRoleMapsByUserId(String userId, String appId, String tenantCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    p2.id,
                    p2.app_id AS appId,
                    p2.name,
                    p2.code,
                    p2.type,
                    p2.weight,
                    p2.description,
                    p2.seq_no AS seqNo,
                    p2.used_app AS usedApp
                FROM platform_role_r_user p1
                LEFT JOIN platform_role p2 ON p2.id = p1.role_id
                WHERE p1.del_status = 0
                  AND p2.del_status = 0
                  AND p2.enable_status = 1
                """);
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotBlank(appId)) {
            sql.append("\n  AND p2.app_id = ?");
            params.add(appId);
        }
        if (StringUtils.isNotBlank(tenantCode)) {
            sql.append("\n  AND p2.tenant_code = ?");
            params.add(tenantCode);
        }
        sql.append("\n  AND p1.user_id = ?");
        sql.append("\nORDER BY p2.seq_no ASC");
        params.add(userId);
        return MetaFactory.sql(sql.toString()).params(params.toArray()).list();
    }

    private List<Tenant> queryTenants(String sql, String value) {
        return MetaFactory.sql(sql)
                .param(value)
                .wrapperResult(this::toTenant)
                .list();
    }

    private UserOrg toUserOrg(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        UserOrg userOrg = new UserOrg();
        userOrg.setOrgId(Optional.ofNullable(row.get("id")).map(Object::toString).orElse(null));
        userOrg.setName(Optional.ofNullable(row.get("name")).map(Object::toString).orElse(null));
        userOrg.setDefaultOrg(Boolean.TRUE.equals(row.get("defaultOrg")) || "1".equals(String.valueOf(row.get("defaultOrg"))));
        userOrg.setPid(Optional.ofNullable(row.get("pid")).map(Object::toString).orElse(null));
        userOrg.setTenantCode(Optional.ofNullable(row.get("tenantCode")).map(Object::toString).orElse(null));
        return userOrg;
    }

    private Tenant toTenant(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new Tenant(
                Optional.ofNullable(row.get("code")).map(Object::toString).orElse(null),
                Optional.ofNullable(row.get("name")).map(Object::toString).orElse(null)
        );
    }
}
