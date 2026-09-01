package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件用户设置实体（用户级 KV；key='general' 为通用设置 JSON）。
 *
 * 表名 mail_setting。(user_id + setting_key) 去重 upsert 由 Service 层保证
 * （动态 schema 无 UK 惯例，同 P0-P2 业务表）。通知开关并入 general.enableNotifications，
 * 不独立建 key。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_setting", catalog = "mail")
@Title(title = "邮件用户设置")
public class MailSetting extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 设置键（general 等） */
    @Title(title = "设置键")
    @Col(name = "setting_key", nullable = false, charMaxlength = 64)
    private String settingKey;

    /** 设置值（JSON） */
    @Title(title = "设置值")
    @Col(name = "setting_value", dataType = "text")
    private String settingValue;
}
