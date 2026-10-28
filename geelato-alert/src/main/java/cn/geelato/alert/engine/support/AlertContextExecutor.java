package cn.geelato.alert.engine.support;

import cn.geelato.alert.engine.EngineConsts;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

/**
 * 告警引擎线程上下文执行器：落库单元按规则租户建立系统主体上下文
 * （否则告警线程无会话，平台填充器会把 creator/tenant_code 覆盖为 null）；
 * 源实例存在参与者时经 {@link #withParticipantPrincipal} 让渡归属（与委托代办同语义）。
 */
public final class AlertContextExecutor {

    private AlertContextExecutor() {
    }

    /** 规则租户系统身份执行：当前线程无会话时注入（系统主体 + 规则租户），finally 清理。 */
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
     * 告警归属身份让渡（与委托代办 applyDelegation 同语义）：生效身份切至源实例参与者
     * （审计归属/委托人），delegate 字段记告警引擎（实际操作人/代理人）；
     * 仅系统主体上下文内可用；源行无参与者（userId 空）时保持引擎身份执行。
     */
    public static <T> T withParticipantPrincipal(String userId, String userName, java.util.function.Supplier<T> task) {
        User current = SecurityContext.getCurrentUser();
        if (current == null || !current.isSystemPrincipal()) {
            throw new IllegalStateException("参与者身份让渡仅可在告警引擎系统主体上下文内使用");
        }
        if (userId == null || userId.isBlank()) {
            return task.get();
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
        user.setUserId(EngineConsts.SYSTEM_USER_ID);
        user.setUserName(EngineConsts.SYSTEM_USER_NAME);
        user.setSystemPrincipal(true);
        return user;
    }
}
