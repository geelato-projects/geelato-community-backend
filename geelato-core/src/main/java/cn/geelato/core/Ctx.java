package cn.geelato.core;

import cn.geelato.core.env.entity.User;

import java.util.HashMap;

/**
 * 上下文件参数，可用于sqlTemplate解析构建时的语句的默认内置参数
 * 也可用于mql命名执行时，获取上下文变量，如$ctx.userId
 * @author geemeta
 *
 */
public class Ctx extends HashMap<String, String> {

    private static final ThreadLocal<User> threadLocalUser = new ThreadLocal<>();

    private static final ThreadLocal<String> threadLocalTenantCode = new ThreadLocal<>();

    public Ctx(){
        this.put("userId",getCurrentUser().getUserId());
        this.put("userName",getCurrentUser().getUserName());
        this.put("defaultOrgId",getCurrentUser().getDefaultOrgId());
        this.put("tenantCode",getCurrentTenantCode());
    }

    public static void setCurrentUser(User user){
        threadLocalUser.set(user);
    }
    public static void setCurrentTenant(String tenantCode){
        threadLocalTenantCode.set(tenantCode);
    }

    public static User getCurrentUser(){
        return threadLocalUser.get();
    }

    public static String getCurrentTenantCode() {
        return threadLocalTenantCode.get();
    }
}
