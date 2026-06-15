package cn.geelato.security;

/**
 * 将当前线程的 {@link SecurityContext}（User、Tenant）传播到异步线程。
 * <p>
 * 用法：
 * <pre>
 * // 自动捕获当前线程上下文
 * CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> {
 *     // 此处可正常使用 SecurityContext.getCurrentUser()
 * }));
 *
 * // 显式指定用户（适用于定时任务等无 HTTP 上下文的场景）
 * CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> { ... }, overrideUser, overrideTenant));
 * </pre>
 */
public class SecurityContextRunnable implements Runnable {

    private final Runnable delegate;
    private final User user;
    private final Tenant tenant;

    private SecurityContextRunnable(Runnable delegate, User user, Tenant tenant) {
        this.delegate = delegate;
        this.user = user;
        this.tenant = tenant;
    }

    @Override
    public void run() {
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(tenant);
        try {
            delegate.run();
        } finally {
            SecurityContext.clear();
        }
    }

    /**
     * 包装 Runnable，自动从当前线程捕获 User/Tenant
     */
    public static Runnable wrap(Runnable runnable) {
        return new SecurityContextRunnable(runnable,
                SecurityContext.getCurrentUser(),
                SecurityContext.getCurrentTenant());
    }

    /**
     * 包装 Runnable，使用显式指定的 User 和 Tenant（适用于无 HTTP 上下文的场景）
     */
    public static Runnable wrap(Runnable runnable, User overrideUser, Tenant overrideTenant) {
        return new SecurityContextRunnable(runnable, overrideUser, overrideTenant);
    }

    /**
     * 包装 Runnable，使用显式指定的 User，Tenant 从当前线程捕获
     */
    public static Runnable wrap(Runnable runnable, User overrideUser) {
        return new SecurityContextRunnable(runnable, overrideUser, SecurityContext.getCurrentTenant());
    }
}
