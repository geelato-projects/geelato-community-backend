package cn.geelato.mail.util;

import cn.geelato.core.SessionCtx;
import cn.geelato.mail.exception.MailAccessException;
import org.springframework.util.StringUtils;

/**
 * 邮件模块 Session 上下文工具类。
 *
 * 封装 SessionCtx 调用，统一异常处理：
 * - getCurrentUserId：未登录抛 MailAccessException(40903)
 * - getCurrentUserName：兜底为 "未知用户"（UI 优雅降级）
 * - getCurrentTenantCode：兜底为 "geelato"（默认租户）
 *
 * 用于 Service 层获取当前操作人，避免散落的 SessionCtx 调用与异常处理。
 */
public final class MailSessionCtx {

    private MailSessionCtx() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     * @throws MailAccessException 未登录或会话已过期
     */
    public static String getCurrentUserId() {
        String userId = safeGetUserId();
        if (!StringUtils.hasText(userId)) {
            throw new MailAccessException("未登录或会话已过期");
        }
        return userId;
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名；缺失时返回 "未知用户"（UI 优雅降级）
     */
    public static String getCurrentUserName() {
        String userName;
        try {
            userName = SessionCtx.getUserName();
        } catch (RuntimeException e) {
            // 会话上下文缺失（无登录用户）时 SessionCtx 抛 NPE，按文档契约优雅降级
            userName = null;
        }
        return StringUtils.hasText(userName) ? userName : "未知用户";
    }

    /**
     * 获取当前租户编码。
     *
     * @return 租户编码；缺失时返回 "geelato"（默认租户）
     */
    public static String getCurrentTenantCode() {
        String tenantCode;
        try {
            tenantCode = SessionCtx.getCurrentTenantCode();
        } catch (RuntimeException e) {
            // 会话上下文缺失（无租户）时 SessionCtx 抛 NPE，按文档契约兜底默认租户
            tenantCode = null;
        }
        return StringUtils.hasText(tenantCode) ? tenantCode : "geelato";
    }

    /**
     * 安全获取用户 ID：会话上下文缺失（无登录用户）时 SessionCtx 抛 NPE，归一为 null，
     * 由调用方按契约处理（getCurrentUserId fail-fast 抛 40903）。
     */
    private static String safeGetUserId() {
        try {
            return SessionCtx.getUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
