package cn.geelato.web.platform.audit.model;

import lombok.Data;

/**
 * 审计日志查询参数。
 *
 * <p>支持按操作人、委托人、业务类型、动作、业务对象、结果、租户、时间范围、关键字模糊匹配等过滤。
 */
@Data
public class AuditLogQuery {

    /** 实际操作人ID（代理人） */
    private String actorId;
    /** 委托人ID（被代理人） */
    private String delegatorId;
    /** 业务类型 */
    private String bizType;
    /** 动作类型 */
    private String operType;
    /** 业务动作名（模糊匹配） */
    private String operName;
    /** 业务对象主键 */
    private String targetId;
    /** 租户编码 */
    private String tenantCode;
    /** 实体名 */
    private String entityName;

    /** 操作时间起（毫秒时间戳） */
    private Long fromTime;
    /** 操作时间止（毫秒时间戳） */
    private Long toTime;

    /** 关键字（模糊匹配 summary / operName / targetName） */
    private String keyword;

    /** 页码（从1开始） */
    private int pageNum = 1;
    /** 每页大小 */
    private int pageSize = 20;
}
