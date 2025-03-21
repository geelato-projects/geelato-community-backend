package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.shiro.ShiroUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

/**
 * Created by hongxueqian on 14-4-12.
 */
@Slf4j
public class SecurityHelper {

    public static ShiroUser getCurrentUser() {
        return (ShiroUser) SecurityUtils.getSubject().getPrincipal();
    }

    public static String getCurrentUserName() {
        ShiroUser user = getCurrentUser();
        return user == null ? null : user.loginName;
    }

    public static String getCurrentUserId() {
        ShiroUser user = getCurrentUser();
        return user == null ? null : user.id;
    }

    public static boolean hasRole(String roleIdentifier) {
        return SecurityUtils.getSubject().hasRole(roleIdentifier);
    }

    public static boolean isPermitted(String permission) {
        return SecurityUtils.getSubject().isPermitted(permission);
    }

}
