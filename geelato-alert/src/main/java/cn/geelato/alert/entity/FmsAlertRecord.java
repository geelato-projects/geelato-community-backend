package cn.geelato.alert.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务告警中心-告警记录实体（聚合缓冲 / 渲染快照 / 投递状态 / 命中清单）。
 *
 * <p>表名 platform_alert_record，{@code active_group_key} 在途组占用唯一、闭环置 NULL；
 * {@code event_ids} 关联命中的 {@code platform_exception} 实例 id 清单。真实聚合冲刷与投递引擎留待后续。
 *
 * <p>关联文档：bea/业务告警中心-数据表设计-2026-09-22.md §5
 */
@Getter
@Setter
@Entity(name = "platform_alert_record", catalog = "geelato")
@Title(title = "告警记录")
public class FmsAlertRecord extends BaseSortableEntity {

    @Title(title = "告警规则编码")
    @Col(name = "alert_code", nullable = false, charMaxlength = 32)
    private String alertCode;

    @Title(title = "冗余名")
    @Col(name = "alert_name", charMaxlength = 128)
    private String alertName;

    @Title(title = "规则口径版本")
    @Col(name = "alert_version", dataType = "int")
    private Integer alertVersion;

    @Title(title = "触发类型", description = "EVENT/SCHEDULE/REPEAT/RESOLVE")
    @Col(name = "trigger_type", nullable = false, charMaxlength = 16)
    private String triggerType;

    @Title(title = "触发异常事件类型")
    @Col(name = "event_type", charMaxlength = 16)
    private String eventType;

    @Title(title = "批次号")
    @Col(name = "batch_no", charMaxlength = 40)
    private String batchNo;

    @Title(title = "聚合身份")
    @Col(name = "group_key", charMaxlength = 160)
    private String groupKey;

    @Title(title = "在途告警组唯一列", description = "闭环置 NULL")
    @Col(name = "active_group_key", charMaxlength = 180)
    private String activeGroupKey;

    @Title(title = "命中异常数")
    @Col(name = "exception_count", nullable = false, dataType = "int")
    private int exceptionCount = 0;

    @Title(title = "命中异常实例id清单JSON")
    @Col(name = "event_ids", dataType = "text")
    private String eventIds;

    @Title(title = "聚合统计快照JSON")
    @Col(name = "stats_json", dataType = "text")
    private String statsJson;

    @Title(title = "渲染快照标题")
    @Col(name = "rendered_title", charMaxlength = 120)
    private String renderedTitle;

    @Title(title = "渲染快照正文")
    @Col(name = "rendered_content", dataType = "text")
    private String renderedContent;

    @Title(title = "接收人清单JSON")
    @Col(name = "receiver_ids", dataType = "text")
    private String receiverIds;

    @Title(title = "接收人名称清单JSON")
    @Col(name = "receiver_names", dataType = "text")
    private String receiverNames;

    @Title(title = "实际投递通道组JSON")
    @Col(name = "channels", dataType = "text")
    private String channels;

    @Title(title = "状态", description = "PENDING/READY/SENT/PARTIAL/FAIL/SILENCED")
    @Col(name = "status", nullable = false, charMaxlength = 16)
    private String status = "PENDING";

    @Title(title = "命中的静默规则id")
    @Col(name = "silence_id", charMaxlength = 32)
    private String silenceId;

    @Title(title = "缓冲冲刷到期时刻")
    @Col(name = "flush_at")
    private Date flushAt;

    @Title(title = "组内首事件时刻")
    @Col(name = "first_event_at")
    private Date firstEventAt;

    @Title(title = "组内末事件时刻")
    @Col(name = "last_event_at")
    private Date lastEventAt;

    @Title(title = "最近发送时刻")
    @Col(name = "last_sent_at")
    private Date lastSentAt;

    @Title(title = "geelato-message关联回查键")
    @Col(name = "biz_key", charMaxlength = 64)
    private String bizKey;

    @Title(title = "投递重试次数")
    @Col(name = "retry_count", nullable = false, dataType = "int")
    private int retryCount = 0;

    @Title(title = "错误信息")
    @Col(name = "error_message", charMaxlength = 512)
    private String errorMessage;

    @Title(title = "首次成功投递时刻")
    @Col(name = "sent_at")
    private Date sentAt;

    @Title(title = "所属应用ID", description = "platform_app.id")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;
}
