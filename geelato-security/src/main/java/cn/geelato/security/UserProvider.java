package cn.geelato.security;

import java.util.Collections;
import java.util.List;

public interface UserProvider {

    default String getUserName(String userId) {
        User user = getUser(userId);
        return user == null ? "" : user.getUserName();
    }

    User getUser(String userId);

    /**
     * 全量用户（含 userRoles/userOrgs/userOrgRoles 挂靠关系，
     * 供 {@link SecurityProvider} 桥接实现求解部门/角色下的人员等反向查询）。
     * 快照式实现应覆写；直连式实现可不支持（返回空列表，桥接的人员求解方法随之返回空）。
     */
    default List<User> getAllUsers() {
        return Collections.emptyList();
    }

    default boolean containsUser(String userId) {
        return getUser(userId) != null;
    }

    default User getUserById(String userId) {
        return getUser(userId);
    }

    User getUserByExtendKey(String extendKey, String type);

    default List<UserRole> getUserRoles(String userId) {
        User user = getUser(userId);
        if (user == null || user.getUserRoles() == null) {
            return Collections.emptyList();
        }
        return user.getUserRoles();
    }

    default List<UserOrg> getUserOrgs(String userId) {
        User user = getUser(userId);
        if (user == null || user.getUserOrgs() == null) {
            return Collections.emptyList();
        }
        return user.getUserOrgs();
    }

    default List<UserOrgRole> getUserOrgRoles(String userId) {
        User user = getUser(userId);
        if (user == null || user.getUserOrgRoles() == null) {
            return Collections.emptyList();
        }
        return user.getUserOrgRoles();
    }

    default String normalizeType(String type) {
        if (type == null || type.isEmpty()) {
            return "";
        }
        if ("loginName".equalsIgnoreCase(type) || "login_name".equalsIgnoreCase(type)) {
            return "loginName";
        }
        if ("weixinUnionId".equalsIgnoreCase(type) || "weixin_unionId".equalsIgnoreCase(type) || "unionId".equalsIgnoreCase(type)) {
            return "weixinUnionId";
        }
        if ("weixinWorkUserId".equalsIgnoreCase(type) || "weixin_work_userId".equalsIgnoreCase(type) || "workUserId".equalsIgnoreCase(type)) {
            return "weixinWorkUserId";
        }
        return type;
    }

    void refresh();
}
