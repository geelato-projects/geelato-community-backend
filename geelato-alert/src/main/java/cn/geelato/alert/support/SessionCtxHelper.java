package cn.geelato.alert.support;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.springframework.util.StringUtils;

/**
 * 业务告警中心模块 Session 上下文工具类（模块自包含）。
 *
 * <p>身份与租户统一从 security 模块的 {@link SecurityContext}（ThreadLocal User/Tenant）读取，
 * 与宿主调度链写入同一体系：
 * <ul>
 *   <li>getCurrentUserId：未登录 fail-fast（IllegalStateException）</li>
 *   <li>getCurrentUserName：兜底 "未知用户"（UI 优雅降级）</li>
 *   <li>getCurrentTenantCode：兜底 "geelato"（默认租户）</li>
 * </ul>
 */
public final class SessionCtxHelper {

    private SessionCtxHelper() {
    }

    public static String getCurrentUserId() {
        User user = SecurityContext.getCurrentUser();
        String userId = user == null ? null : user.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new IllegalStateException("未登录或会话已过期");
        }
        return userId;
    }

    public static String getCurrentUserName() {
        User user = SecurityContext.getCurrentUser();
        String userName = user == null ? null : user.getUserName();
        return StringUtils.hasText(userName) ? userName : "未知用户";
    }

    public static String getCurrentTenantCode() {
        Tenant tenant = SecurityContext.getCurrentTenant();
        String tenantCode = tenant == null ? null : tenant.getCode();
        return StringUtils.hasText(tenantCode) ? tenantCode : "geelato";
    }
}
