package cn.geelato.mail.contact.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件联系人实体（用户级隔离；account_id 为空表示用户级共享联系人）。
 *
 * 表名 mail_contact。分组关联为单列 group_id（前端契约 MailContact.groupId 单值），
 * NULL 表示未分组。(user_id + lower(email)) 去重由 Service 层保证。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_contact", catalog = "mail")
@Title(title = "邮件联系人")
public class MailContact extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 来源邮箱账户ID（mail_account.id；NULL=用户级共享联系人） */
    @Title(title = "邮箱账户ID")
    @Col(name = "account_id", charMaxlength = 32)
    private String accountId;

    /** 所属分组ID（mail_contact_group.id；NULL=未分组） */
    @Title(title = "分组ID")
    @Col(name = "group_id", charMaxlength = 32)
    private String groupId;

    /** 联系人姓名 */
    @Title(title = "姓名")
    @Col(name = "name", nullable = false, charMaxlength = 128)
    private String name;

    /** 邮箱地址 */
    @Title(title = "邮箱地址")
    @Col(name = "email", nullable = false, charMaxlength = 128)
    private String email;

    /** 电话 */
    @Title(title = "电话")
    @Col(name = "phone", charMaxlength = 64)
    private String phone;

    /** 公司/组织 */
    @Title(title = "公司/组织")
    @Col(name = "org", charMaxlength = 128)
    private String org;

    /** 头像URL */
    @Title(title = "头像URL")
    @Col(name = "avatar", charMaxlength = 512)
    private String avatar;

    /** 备注 */
    @Title(title = "备注")
    @Col(name = "notes", charMaxlength = 1024)
    private String notes;
}
