package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件用户标签实体（用户级隔离；account_id 为空表示用户级共享标签）。
 *
 * 表名 mail_label。邮件-标签关联存 mail_message.label_ids JSON 数组
 * （与 flags_json 同风格），写侧经 POST /api/mail/batch op=setLabels。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_label", catalog = "mail")
@Title(title = "邮件标签")
public class MailLabel extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 所属邮箱账户ID（mail_account.id；NULL=用户级共享标签） */
    @Title(title = "邮箱账户ID")
    @Col(name = "account_id", charMaxlength = 32)
    private String accountId;

    /** 标签名称 */
    @Title(title = "标签名称")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    /** 标签颜色（HEX） */
    @Title(title = "标签颜色")
    @Col(name = "color", nullable = false, charMaxlength = 16)
    private String color;

    /** 排序号 */
    @Title(title = "排序号")
    @Col(name = "sort_order", nullable = false, dataType = "int")
    private int sortOrder;
}
