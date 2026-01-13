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
