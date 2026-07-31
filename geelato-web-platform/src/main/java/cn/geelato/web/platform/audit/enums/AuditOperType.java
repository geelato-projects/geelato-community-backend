package cn.geelato.web.platform.audit.enums;

/**
 * 业务审计动作类型。
 *
 * <p>面向业务语义（非底层 CRUD），用于区分"审批运单"和"修改个电话"这类不同性质的操作。
 * 由 {@code @AuditLog} 注解显式声明，或由 ORM 兜底层按 Insert/Update/Delete 归入 CREATE/UPDATE/DELETE。
 */
public enum AuditOperType {

    /** 审批通过 */
    APPROVE("审批"),
    /** 提交 */
    SUBMIT("提交"),
    /** 退回/拒绝 */
    REJECT("退回"),
    /** 转办 */
    TRANSFER("转办"),
    /** 终止/作废 */
    TERMINATE("终止"),
    /** 新增 */
    CREATE("新增"),
    /** 修改 */
    UPDATE("修改"),
    /** 删除 */
    DELETE("删除"),
    /** 导出 */
    EXPORT("导出"),
    /** 自定义（由 operName 给出具体动作名） */
    CUSTOM("");

    private final String defaultName;

    AuditOperType(String defaultName) {
        this.defaultName = defaultName;
    }

    /** 兜底用：当未显式给出动作名时，提供一个默认中文动词。 */
    public String defaultName() {
        return defaultName;
    }
}
