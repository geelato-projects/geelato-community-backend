package cn.geelato.web.platform.graal;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.env.entity.User;

public class GraalUtils {

    /**
     * 获取匿名接口用户
     *
     * @return
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
     * 获取匿名接口用户上下文
     *
     * @return
     */
    public static SessionCtx getCxt() {
        User user = getUser();
        SessionCtx.setCurrentUser(user);
        SessionCtx.setCurrentTenant("geelato");
        return new SessionCtx();
    }

    /**
     * 获取当前租户编码
     *
     * @return
     */
    public static String getCurrentTenantCode() {
        GraalUtils.getCxt();
        return SessionCtx.getCurrentTenantCode();
    }
}
