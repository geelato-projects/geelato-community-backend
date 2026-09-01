package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 过滤器应用历史实体（手动 apply-existing 触发记录）。
 *
 * 表名 mail_filter_apply_log。仅手动 apply-existing 写入；sync 收信钩子高频不写，
 * 避免历史膨胀。GET /api/mail/filters/{id}/apply-history 的数据源。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_filter_apply_log", catalog = "mail")
@Title(title = "邮件过滤器应用历史")
public class MailFilterApplyLog extends BaseSortableEntity {

    /** 过滤器ID（mail_filter.id） */
    @Title(title = "过滤器ID")
    @Col(name = "filter_id", nullable = false, charMaxlength = 32)
    private String filterId;

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 本次应用匹配的邮件数 */
    @Title(title = "应用邮件数")
    @Col(name = "applied_count", nullable = false, dataType = "int")
    private int appliedCount;

    /** 触发人（用户名） */
    @Title(title = "触发人")
    @Col(name = "applied_by", nullable = false, charMaxlength = 64)
    private String appliedBy;

    /** 触发类型（manual=手动 apply-existing） */
    @Title(title = "触发类型")
    @Col(name = "trigger_type", nullable = false, charMaxlength = 16)
    private String triggerType;
}
