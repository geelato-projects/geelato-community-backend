package cn.geelato.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于平台用户表的默认用户快照加载器。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-core）仅保留
 * {@link UserSnapshotLoader} SPI 接口（接口在 geelato-security 模块）。保留原 package
 * （cn.geelato.security）以维持 import 一致性——该 package 本身已是 geelato-security + geelato-core 的 split package，
 * 本次仅将 core 侧的 JDBC 实现迁到 platform 侧。</p>
 */
public class JdbcUserSnapshotLoader implements UserSnapshotLoader {
    private final JdbcTemplate platformJdbcTemplate;

    public JdbcUserSnapshotLoader(JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    @Override
    public UserSnapshot load(UserOrgInfoEnricher userOrgInfoEnricher) {
        Map<String, User> userById = new LinkedHashMap<>();
        Map<String, Map<String, User>> extendIndex = new LinkedHashMap<>();

        List<User> users = platformJdbcTemplate.query(
                "select id, login_name, name, tenant_code, weixin_unionId, weixin_work_userId from platform_user where del_status = 0",
                (rs, rowNum) -> {
                    User u = new User();
                    u.setUserId(rs.getString("id"));
                    u.setLoginName(rs.getString("login_name"));
                    u.setUserName(rs.getString("name"));
                    u.setTenantCode(rs.getString("tenant_code"));
                    u.setWeixinUnionId(rs.getString("weixin_unionId"));
                    u.setWeixinWorkUserId(rs.getString("weixin_work_userId"));
                    return u;
                }
        );
        for (User user : users) {
            putUser(userById, extendIndex, user);
        }
        List<Map<String, Object>> roleMapList = platformJdbcTemplate.queryForList(
                "select ru.user_id, r.id, r.code, r.name, r.type, r.tenant_code " +
                        "from platform_role r join platform_role_r_user ru on r.id = ru.role_id and ru.del_status = 0 " +
                        "where r.del_status = 0"
        );
        for (Map<String, Object> rm : roleMapList) {
            String userId = String.valueOf(rm.get("user_id"));
            User user = userById.get(userId);
            if (user == null) {
                continue;
            }
            List<UserRole> list = user.getUserRoles();
            if (list == null) {
                list = new ArrayList<>();
                user.setUserRoles(list);
            }
            UserRole ur = new UserRole();
            ur.setId(String.valueOf(rm.get("id")));
            ur.setCode(getMapString(rm, "code"));
            ur.setName(String.valueOf(rm.get("name")));
            ur.setType(getMapString(rm, "type"));
            ur.setTenantCode(getMapString(rm, "tenant_code"));
            list.add(ur);
        }
        List<Map<String, Object>> orgMapList = platformJdbcTemplate.queryForList(
                "select user_id, org_id, org_name, default_org from platform_org_r_user where del_status = 0"
        );
        for (Map<String, Object> om : orgMapList) {
            String userId = String.valueOf(om.get("user_id"));
            User user = userById.get(userId);
            if (user == null) {
                continue;
            }
            List<UserOrg> orgs = user.getUserOrgs();
            if (orgs == null) {
                orgs = new ArrayList<>();
                user.setUserOrgs(orgs);
            }
            UserOrg uo = new UserOrg();
            String orgId = String.valueOf(om.get("org_id"));
            uo.setOrgId(orgId);
            uo.setName(String.valueOf(om.get("org_name")));
            Object defaultOrg = om.get("default_org");
            boolean isDefaultOrg = "1".equals(String.valueOf(defaultOrg)) || Boolean.TRUE.equals(defaultOrg);
            uo.setDefaultOrg(isDefaultOrg);
            userOrgInfoEnricher.enrich(uo);
            orgs.add(uo);
            if (isDefaultOrg && orgId != null && !orgId.isEmpty()) {
                user.setOrgId(orgId);
                user.setDefaultOrgId(orgId);
            }
        }
        for (User user : userById.values()) {
            userOrgInfoEnricher.enrich(user);
            if (user.getUserRoles() == null) {
                user.setUserRoles(Collections.emptyList());
            } else {
                user.setUserRoles(Collections.unmodifiableList(new ArrayList<>(user.getUserRoles())));
            }
            if (user.getUserOrgs() == null) {
                user.setUserOrgs(Collections.emptyList());
            } else {
                user.setUserOrgs(Collections.unmodifiableList(new ArrayList<>(user.getUserOrgs())));
            }
        }
        return UserSnapshot.from(userById, extendIndex);
    }

    private String getMapString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private void putUser(Map<String, User> userById, Map<String, Map<String, User>> extendIndex, User user) {
        if (user == null || user.getUserId() == null || user.getUserId().isEmpty()) {
            return;
        }
        userById.put(user.getUserId(), user);
        putExtend(extendIndex, "loginName", user.getLoginName(), user);
        putExtend(extendIndex, "weixinUnionId", user.getWeixinUnionId(), user);
        putExtend(extendIndex, "weixinWorkUserId", user.getWeixinWorkUserId(), user);
    }

    private void putExtend(Map<String, Map<String, User>> extendIndex, String type, String key, User user) {
        if (key == null || key.isEmpty()) {
            return;
        }
        extendIndex.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(key, user);
    }
}
