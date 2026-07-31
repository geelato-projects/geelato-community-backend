package cn.geelato.security;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class SecurityContext {

    private static final ThreadLocal<User> threadUser = new ThreadLocal<>();

    private static final ThreadLocal<Tenant> threadTenant = new ThreadLocal<>();

    private static final ThreadLocal<String> threadPassword = new ThreadLocal<>();

    private static final ThreadLocal<App> threadApp = new ThreadLocal<>();

    public static void setCurrentUser(User user) {
        threadUser.set(user);
    }

    public static void setCurrentTenant(Tenant tenant) {
        threadTenant.set(tenant);
    }
    public static void setCurrentPassword(String password) {
        threadPassword.set(password);
    }

    public static void setCurrentApp(App app) {
        threadApp.set(app);
    }

    public static User getCurrentUser() {
        return threadUser.get();
    }

    public static Tenant getCurrentTenant() {
        return threadTenant.get();
    }
    public static String getCurrentPassword() {
        return threadPassword.get();
    }
    public static App getCurrentApp() {
        return threadApp.get();
    }

    public static void clear() {
        threadUser.remove();
        threadTenant.remove();
        threadPassword.remove();
        threadApp.remove();
    }

    /**
     * 当前是否处于委托代办态（即有被委托人代替当前生效身份在操作）。
     * <p>
     * 判定依据：currentUser 的 delegateUserId 非空。
     * 该值由委托代办机制（DelegateSessionStore + applyDelegation）在请求认证后注入。
     */
    public static boolean isDelegated() {
        User user = getCurrentUser();
        return user != null && user.getDelegateUserId() != null && !user.getDelegateUserId().isEmpty();
    }

    /**
     * 获取当前操作的实际操作人（被委托人）ID，未处于委托代办态时返回 null。
     */
    public static String getDelegateUserId() {
        User user = getCurrentUser();
        return user == null ? null : user.getDelegateUserId();
    }

    public static boolean isAdmin() {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        List<UserRole> roles = user.getUserRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (UserRole role : roles) {
            if(role.getCode().contains("admin"))
                return true;
        }
        return false;
    }
}
