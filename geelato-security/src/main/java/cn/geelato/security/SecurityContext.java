package cn.geelato.security;

import java.util.List;
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


    public static boolean isDelegated() {
        User user = getCurrentUser();
        return user != null && user.getDelegateUserId() != null && !user.getDelegateUserId().isEmpty();
    }


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

    /**
     * 当前线程是否以系统后台主体身份运行（定时任务等无 HTTP 上下文的场景）。
     * <p>
     * 判定依据：currentUser 的 systemPrincipal 标志为 true。
     * 该值由后台任务在建立上下文时显式设置（如 {@code ScheduleHelper.wrapWithContext}）。
     */
    public static boolean isSystemPrincipal() {
        User user = getCurrentUser();
        return user != null && user.isSystemPrincipal();
    }
}
