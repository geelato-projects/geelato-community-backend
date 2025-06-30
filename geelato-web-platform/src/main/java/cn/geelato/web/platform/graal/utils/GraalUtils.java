package cn.geelato.web.platform.graal.utils;

import cn.geelato.core.SessionCtx;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

public class GraalUtils {

    /**
     * 获取匿名接口用户的信息。
     * <p>
     * 该方法返回一个预配置的User对象，其中包含匿名接口用户的相关信息。
     *
     * @return 返回一个包含匿名接口用户信息的User对象。
     */
    public static User getUser() {
        User user = new User();
        user.setUserId("5652383759748698112");
        user.setUserName("匿名接口用户");
        user.setLoginName("AnonymityApi");
        user.setDefaultOrgId("4938722966595833856");
        user.setDefaultOrgName("合作单位");
        user.setBuId("3640431826809946145");
        return user;
    }

    /**
     * 获取匿名接口用户上下文。
     * <p>
     * 此方法用于获取当前匿名接口的用户上下文信息，包括当前用户信息和租户信息，并返回一个SessionCtx对象。
     *
     * @return 返回包含当前用户上下文信息的SessionCtx对象。
     */
    public static SessionCtx getCxt() {
        User user = getUser();
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));
        return new SessionCtx();
    }

    /**
     * 获取当前租户编码。
     *
     * @return 返回当前租户的编码。
     */
    public static String getCurrentTenantCode() {
        GraalUtils.getCxt();
        return SessionCtx.getCurrentTenantCode();
    }
}
