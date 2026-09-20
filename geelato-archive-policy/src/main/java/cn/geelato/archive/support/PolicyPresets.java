package cn.geelato.archive.support;

import cn.geelato.archive.enums.PolicyTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台高膨胀表的预置归档策略模板。
 * <p>启动时幂等装载（code 已存在则跳过，不覆盖用户改动），全部默认停用——
 * 用户核阅后在归档策略管理中显式启用。audit 模板的默认保留期 730 天与
 * AuditLogProperties.retentionDays（"审计日志保留天数（供后续归档清理任务使用）"）语义对齐。</p>
 */
public final class PolicyPresets {

    public static class Preset {
        public final String code;
        public final String name;
        public final String tableName;
        public final int retentionDays;

        public Preset(String code, String name, String tableName, int retentionDays) {
            this.code = code;
            this.name = name;
            this.tableName = tableName;
            this.retentionDays = retentionDays;
        }
    }

    public static final List<Preset> PRESETS = buildPresets();

    private static List<Preset> buildPresets() {
        List<Preset> list = new ArrayList<>();
        // 审计日志：每次业务写操作一行，detail_json 大文本；保留期对齐 AuditLogProperties.retentionDays
        list.add(new Preset("tpl-audit-log", "审计日志归档（预置）", "platform_audit_log", 730));
        // 发号流水：每次发号一行，行数增长最快
        list.add(new Preset("tpl-encoding-log", "发号流水归档（预置）", "platform_encoding_log", 365));
        // 通知与收件人明细
        list.add(new Preset("tpl-notification", "站内通知归档（预置）", "platform_notification", 180));
        list.add(new Preset("tpl-notification-user", "通知收件明细归档（预置）", "platform_notification_user", 180));
        // 邮件正文 longtext 存库，体积收益最大
        list.add(new Preset("tpl-mail-message", "邮件消息归档（预置）", "mail_message", 365));
        // 设计器页面源码历史版本，source_content 为 longText
        list.add(new Preset("tpl-app-page-log", "页面版本历史归档（预置）", "platform_app_page_log", 180));
        // 调度执行日志
        list.add(new Preset("tpl-schedule-log", "调度日志归档（预置）", "platform_schedule_log", 90));
        return List.copyOf(list);
    }

    /** 模板默认策略类型：全部为 DEFAULT（create_at 窗口） */
    public static final String PRESET_POLICY_TYPE = PolicyTypeEnum.DEFAULT.name();

    private PolicyPresets() {
    }
}
