package cn.geelato.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DefaultUserProvider extends UserProvider {
    private final JdbcTemplate platformJdbcTemplate;
    public DefaultUserProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        loadData(null);
    }

    @Override
    public void loadData(Object userData) {
        List<User> users = platformJdbcTemplate.query(
                "select * from platform_user where del_status = 0",
                (rs, rowNum) -> {
                    User u = new User();
                    u.setUserId(rs.getString("id"));
                    u.setLoginName(rs.getString("login_name"));
                    u.setUserName(rs.getString("name"));
                    u.setTenantCode(rs.getString("tenant_code"));
                    return u;
                }
        );
        for (User u : users) {
            if (u.getUserId() != null && !u.getUserId().isEmpty()) {
                userDataMap.put(u.getUserId(), u);
            }
        }
        List<Map<String, Object>> roleMapList = platformJdbcTemplate.queryForList(
                "select ru.user_id, r.id, r.code, r.name, r.type, r.tenant_code " +
                        "from platform_role r join platform_role_r_user ru on r.id = ru.role_id and ru.del_status = 0 " +
                        "where r.del_status = 0"
        );
        for (Map<String, Object> rm : roleMapList) {
            String userId = String.valueOf(rm.get("user_id"));
            User user = userDataMap.get(userId);
            if (user == null) continue;
            List<UserRole> list = user.getUserRoles();
            if (list == null) {
                list = new java.util.ArrayList<>();
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
                "select user_id, org_id, org_name from platform_org_r_user where del_status = 0"
        );
        for (Map<String, Object> om : orgMapList) {
            String userId = String.valueOf(om.get("user_id"));
            User user = userDataMap.get(userId);
            if (user == null) continue;
            List<UserOrg> orgs = user.getUserOrgs();
            if (orgs == null) {
                orgs = new java.util.ArrayList<>();
                user.setUserOrgs(orgs);
            }
            UserOrg uo = new UserOrg();
            uo.setOrgId(String.valueOf(om.get("org_id")));
            uo.setName(String.valueOf(om.get("org_name")));
            orgs.add(uo);
        }
    }

    private String getMapString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
