package cn.geelato.security;

public class SecurityContext {

    private static final ThreadLocal<User> threadUser = new ThreadLocal<>();

    private static final ThreadLocal<Tenant> threadTenant = new ThreadLocal<>();

    private static final ThreadLocal<String> threadPassword = new ThreadLocal<>();

    public static void setCurrentUser(User user) {
        threadUser.set(user);
    }

    public static void setCurrentTenant(Tenant tenant) {
        threadTenant.set(tenant);
    }
    public static void setCurrentPassword(String password) {
        threadPassword.set(password);
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
}
