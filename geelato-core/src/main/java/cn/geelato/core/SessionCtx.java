package cn.geelato.core;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;

import java.util.HashMap;

/**
 * 上下文件参数，可用于sqlTemplate解析构建时的语句的默认内置参数
 * 也可用于mql命名执行时，获取上下文变量，如$ctx.userId
 * @author geemeta
 *
 */
public class SessionCtx extends HashMap<String,String> {
        public SessionCtx(){
        putIfPresent("userId", getUserId());
        putIfPresent("userName", getUserName());
        putIfPresent("orgId", getOrgId());
        putIfPresent("defaultOrgId", getDefaultOrgId());
        putIfPresent("tenantCode", getCurrentTenantCode());
    }

    public static String getUserId(){
        User user = getCurrentUser();
        return user == null ? null : user.getUserId();
    }
    public static String getUserName(){
        User user = getCurrentUser();
        return user == null ? null : user.getUserName();
    }

    public static String getOrgId() {
        User user = getCurrentUser();
        return user == null ? null : user.getOrgId();
    }

    public static String getDefaultOrgId() {
        User user = getCurrentUser();
        return user == null ? null : user.getDefaultOrgId();
    }
    public static User getCurrentUser(){
        return SecurityContext.getCurrentUser();
    }

    public static String getCurrentTenantCode() {
        return SecurityContext.getCurrentTenant() == null ? null : SecurityContext.getCurrentTenant().getCode();
    }

    private void putIfPresent(String key, String value) {
        if (value != null) {
            this.put(key, value);
        }
    }

}
