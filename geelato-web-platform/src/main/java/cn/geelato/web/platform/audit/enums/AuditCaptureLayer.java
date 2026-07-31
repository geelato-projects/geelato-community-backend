package cn.geelato.web.platform.audit.enums;

/** 审计记录的捕获层，标识该条记录来自哪一层捕获。 */
public enum AuditCaptureLayer {
    /** 第1层：声明式业务动作（{@code @AuditLog} 注解触发） */
    ANNOTATED,
    /** 第2层：ORM 兜底（未被注解覆盖的写操作自动记录） */
    ORM_FALLBACK
}
