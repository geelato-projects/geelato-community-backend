package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件个性签名实体（用户级隔离；account_id 为空表示用户级共享签名）。
 *
 * 表名 mail_signature。is_default 每用户唯一由 Service 层保证
 * （设默认时清除该用户其他签名的默认标记）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_signature", catalog = "mail")
@Title(title = "邮件签名")
public class MailSignature extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 所属邮箱账户ID（mail_account.id；NULL=用户级共享签名） */
    @Title(title = "邮箱账户ID")
    @Col(name = "account_id", charMaxlength = 32)
    private String accountId;

    /** 签名名称 */
    @Title(title = "签名名称")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    /** 签名内容（HTML） */
    @Title(title = "签名内容")
    @Col(name = "content", dataType = "text")
    private String content;

    /** 是否默认签名 */
    @Title(title = "是否默认签名")
    @Col(name = "is_default", nullable = false, dataType = "int")
    private int isDefault;
}
