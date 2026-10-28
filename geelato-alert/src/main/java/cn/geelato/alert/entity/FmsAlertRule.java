package cn.geelato.alert.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务告警中心-告警规则实体。
 *
 * <p>表名 platform_alert_rule，UK(alert_code, del_status, delete_at)。
 * 一行承载告警规则的匹配/触发/聚合/接收人/路由/模板绑定全部定义。
 * {@code definition_json} 存编辑器 RuleDocument 原文，作为前端无损 round-trip 的可信来源。
 *
 * <p>关联文档：bea/业务告警中心-数据表设计-2026-09-22.md §3
 */
@Getter
@Setter
@Entity(name = "platform_alert_rule", catalog = "geelato")
@Title(title = "告警规则")
public class FmsAlertRule extends BaseSortableEntity {

    @Title(title = "告警规则编码")
    @Col(name = "alert_code", nullable = false, charMaxlength = 32)
    private String alertCode;

    @Title(title = "告警名称")
    @Col(name = "alert_name", nullable = false, charMaxlength = 128)
    private String alertName;

    @Title(title = "描述")
    @Col(name = "alert_desc", charMaxlength = 1024)
    private String alertDesc;

    @Title(title = "事件源匹配条件JSON", description = "{source,events,ruleCodes}，结构由 source 决定")
    @Col(name = "match_json", dataType = "text")
    private String matchJson;

    @Title(title = "触发模式", description = "EVENT/SCHEDULE/BOTH")
    @Col(name = "trigger_mode", nullable = false, charMaxlength = 16)
    private String triggerMode = "EVENT";

    @Title(title = "调度周期")
    @Col(name = "cron_expression", charMaxlength = 32)
    private String cronExpression;

    @Title(title = "数据集口径JSON")
    @Col(name = "dataset_json", dataType = "text")
    private String datasetJson;

    @Title(title = "聚合模式", description = "INDEPENDENT/AGGREGATE")
    @Col(name = "aggregate_mode", nullable = false, charMaxlength = 16)
    private String aggregateMode = "INDEPENDENT";

    @Title(title = "聚合窗口秒")
    @Col(name = "group_wait_seconds", dataType = "int")
    private Integer groupWaitSeconds;

    @Title(title = "再通知最小间隔秒")
    @Col(name = "group_interval_seconds", dataType = "int")
    private Integer groupIntervalSeconds;

    @Title(title = "在途组重复提醒间隔分钟")
    @Col(name = "repeat_interval_minutes", dataType = "int")
    private Integer repeatIntervalMinutes;

    @Title(title = "清单渲染条数上限")
    @Col(name = "max_items", dataType = "int")
    private Integer maxItems;

    @Title(title = "全部闭环恢复通知", description = "1发/0不发")
    @Col(name = "resolve_notify", nullable = false, dataType = "int")
    private int resolveNotify = 0;

    @Title(title = "接收人JSON", description = "接收人选择器 {extra[]}")
    @Col(name = "receiver_json", dataType = "text")
    private String receiverJson;

    @Title(title = "通道路由JSON", description = "{default:{channels,templateCode}}")
    @Col(name = "route_json", dataType = "text")
    private String routeJson;

    @Title(title = "每日发送上限条")
    @Col(name = "daily_limit", dataType = "int")
    private Integer dailyLimit;

    @Title(title = "启用状态", description = "0停用/1生效")
    @Col(name = "enable_status", nullable = false, dataType = "int")
    private int enableStatus = 0;

    @Title(title = "口径版本")
    @Col(name = "version", nullable = false, dataType = "int")
    private int version = 1;

    @Title(title = "所属应用ID", description = "platform_app.id")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;
}
