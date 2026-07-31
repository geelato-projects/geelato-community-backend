package cn.geelato.web.platform.audit.context;

import lombok.Builder;
import lombok.Data;

/**
 * 审计身份/环境快照。
 *
 * <p>从 {@code SecurityContext}（ThreadLocal）与 HTTP 请求中一次性采集，捕获后即脱离线程，
 * 供异步落库线程安全使用。两层捕获共用此快照。
 */
@Data
@Builder
public class AuditActorSnapshot {

    /** 实际操作人ID（代理人） */
    private String actorId;
    /** 实际操作人名称（代理人） */
    private String actorName;
    /** 操作人类型：USER/SYSTEM/SCHEDULED/ANONYMOUS */
    private String actorType;

    /** 委托人ID（被代理人），无委托则空 */
    private String delegatorId;
    /** 委托人名称（被代理人） */
    private String delegatorName;

    private String tenantCode;
    private String orgId;
    private String deptId;
    private String buId;

    private String clientId;
    private String sessionId;
    private String ip;
    private String userAgent;

    /** 当前 HTTP 请求ID */
    private String requestId;
    /** 链路追踪ID */
    private String traceId;
}
