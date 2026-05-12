package cn.geelato.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultUserProvider extends UserProvider {
    private final JdbcTemplate platformJdbcTemplate;
    private final Map<String, Map<String, User>> extendUserPool = new ConcurrentHashMap<>();

    public DefaultUserProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        loadData(null);
    }

    @Override
    public void loadData(Object userData) {
        userDataMap.clear();
        extendUserPool.clear();

        List<User> users = platformJdbcTemplate.query(
                "select * from platform_user where del_status = 0",
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
        putUsers(users);
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

    @Override
    public User getUserByExtendKey(String extendKey, String type) {
        if (extendKey == null || extendKey.isEmpty()) {
            return null;
        }
        String normalized = normalizeType(type);
        Map<String, User> pool = extendUserPool.get(normalized);
        if (pool != null) {
            User user = pool.get(extendKey);
            if (user != null) {
                return user;
            }
        }
        return super.getUserByExtendKey(extendKey, type);
    }

    @Override
    protected void putUser(User user) {
        super.putUser(user);
        if (user == null) {
            return;
        }
        putExtend("loginName", user.getLoginName(), user);
        putExtend("weixinUnionId", user.getWeixinUnionId(), user);
        putExtend("weixinWorkUserId", user.getWeixinWorkUserId(), user);
    }

    private void putExtend(String type, String key, User user) {
        if (key == null || key.isEmpty()) {
            return;
        }
        extendUserPool.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(key, user);
    }
}
