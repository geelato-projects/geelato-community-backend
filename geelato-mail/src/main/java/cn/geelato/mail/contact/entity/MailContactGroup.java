package cn.geelato.mail.contact.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件联系人分组实体（用户级隔离）。
 *
 * 表名 mail_contact_group。contactCount 不落列，读取时按当前用户实时聚合；
 * 删除分组时该组联系人 group_id 置 NULL（未分组），不删除联系人。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_contact_group", catalog = "mail")
@Title(title = "邮件联系人分组")
public class MailContactGroup extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 分组名称 */
    @Title(title = "分组名称")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    /** 排序号 */
    @Title(title = "排序号")
    @Col(name = "sort_order", nullable = false, dataType = "int")
    private int sortOrder;
}
