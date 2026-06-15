package cn.geelato.security;

import java.util.Collections;
import java.util.List;

public interface UserProvider {

    default String getUserName(String userId) {
        User user = getUser(userId);
        return user == null ? "" : user.getUserName();
    }

    User getUser(String userId);

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
