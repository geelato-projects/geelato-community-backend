package cn.geelato.web.platform;

import cn.geelato.core.env.entity.User;

/**
 * @author diabl
 */
public class PlatformContext {

    private static final ThreadLocal<User> threadLocalUser = new ThreadLocal<>();

    private static final ThreadLocal<Tenant> threadLocalTenant = new ThreadLocal<>();

    public static void setCurrentUser(User user) {
        threadLocalUser.set(user);
    }

    public static void setCurrentTenant(Tenant tenant) {
        threadLocalTenant.set(tenant);
    }

    public static User getCurrentUser() {
        return threadLocalUser.get();
    }

    public static Tenant getCurrentTenant() {
        return threadLocalTenant.get();
    }
}
