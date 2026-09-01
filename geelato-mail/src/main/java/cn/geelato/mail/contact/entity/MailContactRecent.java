package cn.geelato.mail.contact.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 最近收件人实体（撰写页收件人联想 suggest 的数据源之一）。
 *
 * 表名 mail_contact_recent。去重键 (user_id, lower(email))，写入时 upsert
 * （use_count+1 / last_used_at 刷新）；每用户上限 200 条，超出按 last_used_at
 * 最旧淘汰（Service 层执行）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_contact_recent", catalog = "mail")
@Title(title = "邮件最近收件人")
public class MailContactRecent extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 收件人邮箱地址 */
    @Title(title = "邮箱地址")
    @Col(name = "email", nullable = false, charMaxlength = 128)
    private String email;

    /** 收件人显示名 */
    @Title(title = "显示名")
    @Col(name = "name", charMaxlength = 128)
    private String name;

    /** 使用次数 */
    @Title(title = "使用次数")
    @Col(name = "use_count", nullable = false, dataType = "int")
    private int useCount;

    /** 最近使用时间 */
    @Title(title = "最近使用时间")
    @Col(name = "last_used_at", nullable = false)
    private Date lastUsedAt;
}
