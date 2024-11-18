package cn.geelato.web.platform.m.security.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hongxueqian on 14-4-12.
 */
@Slf4j
public class SecurityHelper {

    public static ShiroDbRealm.ShiroUser getCurrentUser() {
        return (ShiroDbRealm.ShiroUser) SecurityUtils.getSubject().getPrincipal();
    }

    /**
     * 取出Shiro中的当前用户LoginName.
     */
    public static String getCurrentUserName() {
        ShiroDbRealm.ShiroUser user = getCurrentUser();
        return user == null ? null : user.loginName;
    }

    public static String getCurrentUserId() {
        ShiroDbRealm.ShiroUser user = getCurrentUser();
        return user == null ? null : user.id;
    }

    public static boolean hasRole(String roleIdentifier) {
        return SecurityUtils.getSubject().hasRole(roleIdentifier);
    }

    public static boolean isPermitted(String permission) {
        return SecurityUtils.getSubject().isPermitted(permission);
    }

    public static boolean isAuthenticatedForCurrentUser() {
        Subject currentUser = SecurityUtils.getSubject();
        if (currentUser == null) {
            return false;
        } else {
            return currentUser.isAuthenticated();
        }
    }
}
