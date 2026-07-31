package cn.geelato.web.platform.audit.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.web.platform.audit.context.AuditActorSnapshot;
import cn.geelato.web.platform.audit.context.AuditContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 统一采集审计身份/委托/环境信息，产出 {@link AuditActorSnapshot}。
 *
 * <p>信息来源：
 * <ul>
 *   <li>{@link SecurityContext}（ThreadLocal）：actor、delegator、tenant、org/dept/bu</li>
 *   <li>{@link SessionCtx}：兜底取 userId/userName/orgId/tenantCode</li>
 *   <li>HTTP 请求：ip、userAgent、clientId、sessionId</li>
 *   <li>{@link AuditContext}：请求ID</li>
 * </ul>
 *
 * <p>采集逻辑对缺失场景（无登录用户、非 HTTP 线程）做了容错，降级为 ANONYMOUS/SYSTEM。
 */
@Slf4j
@Component
public class AuditContextProvider {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    public AuditActorSnapshot snapshot() {
        User user = SecurityContext.getCurrentUser();
        Tenant tenant = SecurityContext.getCurrentTenant();

        String actorId;
        String actorName;
        String actorType;
        String tenantCode;
        String orgId;
        String deptId;
        String buId;
        String delegatorId = null;
        String delegatorName = null;

        if (user != null) {
            // 当前生效身份即为 actor（委托代办时，SecurityContext 已把 target 用户作为 currentUser）
            actorId = nvl(user.getUserId(), SessionCtx.getUserId());
            actorName = nvl(user.getUserName(), SessionCtx.getUserName());
            actorType = "USER";
            tenantCode = nvl(user.getTenantCode(),
                    tenant != null ? tenant.getCode() : SessionCtx.getCurrentTenantCode());
            orgId = nvl(user.getOrgId(), SessionCtx.getOrgId());
            deptId = user.getDeptId();
            buId = user.getBuId();
            // 委托代办：当前操作实为代被代理人执行
            if (SecurityContext.isDelegated()) {
                delegatorId = SecurityContext.getDelegateUserId();
                delegatorName = user.getDelegateUserName();
            }
        } else {
            // 无登录用户：定时任务/系统调用
            boolean isHttp = isWebRequestActive();
            actorId = "system";
            actorName = "系统";
            actorType = isHttp ? "ANONYMOUS" : "SYSTEM";
            tenantCode = tenant != null ? tenant.getCode() : null;
            orgId = deptId = buId = null;
        }

        AuditActorSnapshot.AuditActorSnapshotBuilder b = AuditActorSnapshot.builder()
                .actorId(actorId)
                .actorName(actorName)
                .actorType(actorType)
                .delegatorId(delegatorId)
                .delegatorName(delegatorName)
                .tenantCode(tenantCode)
                .orgId(orgId)
                .deptId(deptId)
                .buId(buId);

        // HTTP 请求相关（非 HTTP 线程则留空）
        HttpServletRequest req = currentRequest();
        if (req != null) {
            b.ip(resolveIp(req))
                    .userAgent(req.getHeader("User-Agent"))
                    .sessionId(req.getSession(false) != null ? req.getSession(false).getId() : null)
                    .clientId(req.getHeader("X-Client-Id"));
        }

        // traceId：优先请求头，否则用 requestId 兜底
        String traceId = req != null ? req.getHeader("X-Trace-Id") : null;
        String requestId = AuditContext.current().getRequestId();
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        b.requestId(requestId).traceId(nvl(traceId, requestId));

        return b.build();
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWebRequestActive() {
        return currentRequest() != null;
    }

    private String resolveIp(HttpServletRequest req) {
        for (String h : IP_HEADERS) {
            String v = req.getHeader(h);
            if (StringUtils.hasText(v) && !"unknown".equalsIgnoreCase(v)) {
                int comma = v.indexOf(',');
                return (comma > 0 ? v.substring(0, comma) : v).trim();
            }
        }
        return req.getRemoteAddr();
    }

    private String nvl(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
