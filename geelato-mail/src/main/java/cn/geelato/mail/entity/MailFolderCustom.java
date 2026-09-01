package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件自定义文件夹实体（用户自建，按 account_id + user_id 隔离）。
 *
 * 表名 mail_folder_custom。邮件移动到自定义文件夹时，
 * mail_message.folder 存 "custom:{id}" 形式（见 MailFolderController）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_folder_custom", catalog = "mail")
@Title(title = "邮件自定义文件夹")
public class MailFolderCustom extends BaseSortableEntity {

    /** 所属邮箱账户ID（mail_account.id） */
    @Title(title = "邮箱账户ID")
    @Col(name = "account_id", nullable = false, charMaxlength = 32)
    private String accountId;

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 文件夹名称 */
    @Title(title = "文件夹名称")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    /** 父文件夹ID（一级为空） */
    @Title(title = "父文件夹ID")
    @Col(name = "parent_id", charMaxlength = 32)
    private String parentId;

    /** 排序号 */
    @Title(title = "排序号")
    @Col(name = "sort_order", nullable = false, dataType = "int")
    private int sortOrder;
}
