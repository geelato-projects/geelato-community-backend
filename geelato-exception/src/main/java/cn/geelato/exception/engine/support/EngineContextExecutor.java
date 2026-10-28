package cn.geelato.exception.engine.support;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import lombok.extern.slf4j.Slf4j;

/**
 * 引擎线程上下文执行器：调度/事件线程复用会残留上一任务的租户与数据源键，
 * 执行前重建（系统账号 + 规则行租户 + 主库键）、finally 双出口清理。
 */
@Slf4j
public final class EngineContextExecutor {

    public static final String SYSTEM_USER_ID = "exception-engine";
    public static final String SYSTEM_USER_NAME = "异常引擎";

    private EngineContextExecutor() {
    }

    public static <T> T withContext(String tenantCode, java.util.function.Supplier<T> task) {
        boolean injected = SecurityContext.getCurrentUser() == null;
        if (injected) {
            SecurityContext.setCurrentUser(systemUser());
            SecurityContext.setCurrentTenant(new Tenant(tenantCode == null || tenantCode.isBlank() ? "geelato" : tenantCode));
        }
        try {
            return task.get();
        } finally {
            if (injected) {
                SecurityContext.clear();
            }
        }
    }

    /**
     * 跨租户系统身份执行：systemPrincipal=true 跳过数据权限过滤，不设租户则租户过滤不追加，
     * 用于调度 tick / 事件匹配扫全量规则表；结束后恢复调用线程原有上下文。
     */
    public static <T> T withSystemContext(java.util.function.Supplier<T> task) {
        User previousUser = SecurityContext.getCurrentUser();
        Tenant previousTenant = SecurityContext.getCurrentTenant();
        SecurityContext.setCurrentUser(systemUser());
        SecurityContext.setCurrentTenant(null);
        try {
            return task.get();
        } finally {
            SecurityContext.setCurrentUser(previousUser);
            SecurityContext.setCurrentTenant(previousTenant);
        }
    }

    /**
     * 引擎执行统一入口（adminScan 等由真实用户会话的线程触发时使用）：
     * 无论调用线程原有身份，一律强制重建系统主体 + 规则租户，结束后恢复。
     */
    public static <T> T withEngineContext(String tenantCode, java.util.function.Supplier<T> task) {
        User previousUser = SecurityContext.getCurrentUser();
        Tenant previousTenant = SecurityContext.getCurrentTenant();
        SecurityContext.setCurrentUser(systemUser());
        SecurityContext.setCurrentTenant(new Tenant(tenantCode == null || tenantCode.isBlank() ? "geelato" : tenantCode));
        try {
            return task.get();
        } finally {
            SecurityContext.setCurrentUser(previousUser);
            SecurityContext.setCurrentTenant(previousTenant);
        }
    }

    /**
     * 实例归属身份让渡（与委托代办 applyDelegation 同语义）：生效身份切至参与者（审计归属/委托人），
     * delegate 字段记引擎系统身份（实际操作人/代理人）；仅系统主体上下文内可用，结束后恢复。
     * 包裹范围应仅覆盖落库语句，包裹内不做查询（数据权限将按参与者身份计算）。
     */
    public static <T> T withParticipantPrincipal(String userId, String userName, java.util.function.Supplier<T> task) {
        User current = SecurityContext.getCurrentUser();
        if (current == null || !current.isSystemPrincipal()) {
            throw new IllegalStateException("参与者身份让渡仅可在引擎系统主体上下文内使用");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("参与者身份让渡缺少参与者 userId");
        }
        User principal = new User();
        principal.setUserId(userId);
        principal.setUserName(userName);
        principal.setDelegateUserId(current.getUserId());
        principal.setDelegateUserName(current.getUserName());
        SecurityContext.setCurrentUser(principal);
        try {
            return task.get();
        } finally {
            SecurityContext.setCurrentUser(current);
        }
    }

    private static User systemUser() {
        User user = new User();
        user.setUserId(SYSTEM_USER_ID);
        user.setUserName(SYSTEM_USER_NAME);
        // systemPrincipal 由平台侧 PlatformQueryFilterSupport 消费：后台主体跳过数据权限（不追加 creator= 条件）
        user.setSystemPrincipal(true);
        return user;
    }
}
