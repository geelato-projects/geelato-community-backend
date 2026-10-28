package cn.geelato.alert.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务告警中心-告警消息模板实体（按渠道变体，UK(template_code, channel)）。
 *
 * <p>表名 platform_alert_template，一行为某模板编码在某渠道下的标题/正文渲染脚本与示例上下文。
 * {@code definition_json} 存编辑器 TemplateDocument 原文，作为前端无损 round-trip 的可信来源。
 *
 * <p>关联文档：bea/业务告警中心-数据表设计-2026-09-22.md §4
 */
@Getter
@Setter
@Entity(name = "platform_alert_template", catalog = "geelato")
@Title(title = "告警消息模板")
public class FmsAlertTemplate extends BaseSortableEntity {

    @Title(title = "模板编码")
    @Col(name = "template_code", nullable = false, charMaxlength = 32)
    private String templateCode;

    @Title(title = "模板名称")
    @Col(name = "template_name", nullable = false, charMaxlength = 128)
    private String templateName;

    @Title(title = "渠道变体", description = "default/inapp/email/sms/wework/bot")
    @Col(name = "channel", nullable = false, charMaxlength = 16)
    private String channel = "default";

    @Title(title = "用途说明")
    @Col(name = "template_desc", charMaxlength = 512)
    private String templateDesc;

    @Title(title = "标题渲染脚本")
    @Col(name = "title_script", nullable = false, dataType = "text")
    private String titleScript;

    @Title(title = "正文渲染脚本")
    @Col(name = "content_script", nullable = false, dataType = "text")
    private String contentScript;

    @Title(title = "示例上下文JSON")
    @Col(name = "context_sample_json", dataType = "text")
    private String contextSampleJson;

    @Title(title = "上下文类型", description = "SINGLE/GROUP/SCHEDULE")
    @Col(name = "context_type", charMaxlength = 16)
    private String contextType;

    @Title(title = "版本")
    @Col(name = "version", nullable = false, dataType = "int")
    private int version = 1;

    @Title(title = "引擎版本", description = "脚本契约快照（alertVersion 短版本，空=v1）")
    @Col(name = "engine_version", charMaxlength = 8)
    private String engineVersion;

    @Title(title = "启用状态", description = "0停用/1生效")
    @Col(name = "enable_status", nullable = false, dataType = "int")
    private int enableStatus = 1;

    @Title(title = "所属应用ID", description = "platform_app.id")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;
}
