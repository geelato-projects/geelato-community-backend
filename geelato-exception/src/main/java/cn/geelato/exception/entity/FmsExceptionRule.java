package cn.geelato.exception.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务异常预警中心-异常规则实体。
 *
 * <p>表名 platform_exception_rule，UK(rule_code, del_status, delete_at)。
 * 一行一条规则的全部定义（类型/触发/判定/参与者/处置/风暴/运行态）。
 * {@code definition_json} 存编辑器 RuleDocument 原文，作为前端无损 round-trip 的可信来源。
 *
 * <p>关联文档：bea/业务异常预警中心-数据表设计-2026-09-17.md §3
 */
@Getter
@Setter
@Entity(name = "platform_exception_rule", catalog = "geelato")
@Title(title = "异常规则")
public class FmsExceptionRule extends BaseSortableEntity {

    @Title(title = "规则编码")
    @Col(name = "rule_code", nullable = false, charMaxlength = 32)
    private String ruleCode;

    @Title(title = "规则名称")
    @Col(name = "rule_name", nullable = false, charMaxlength = 128)
    private String ruleName;

    @Title(title = "业务域编码")
    @Col(name = "domain_code", charMaxlength = 16)
    private String domainCode;

    @Title(title = "规则描述")
    @Col(name = "rule_desc", charMaxlength = 1024)
    private String ruleDesc;

    @Title(title = "等级", description = "P0~P3")
    @Col(name = "level", charMaxlength = 2)
    private String level;

    @Title(title = "规则类型", description = "ALERT/ESCALATE")
    @Col(name = "rule_kind", nullable = false, charMaxlength = 16)
    private String ruleKind = "ALERT";

    @Title(title = "升级关联规则编码")
    @Col(name = "escalation_of_rule_code", charMaxlength = 32)
    private String escalationOfRuleCode;

    @Title(title = "触发类型", description = "CHANGE/SCAN/BOTH")
    @Col(name = "trigger_type", nullable = false, charMaxlength = 16)
    private String triggerType = "SCAN";

    @Title(title = "扫描周期")
    @Col(name = "cron_expression", charMaxlength = 32)
    private String cronExpression;

    @Title(title = "订阅入口实体JSON")
    @Col(name = "listen_entities", dataType = "text")
    private String listenEntities;

    @Title(title = "判定管道JSON")
    @Col(name = "pipeline_json", dataType = "text")
    private String pipelineJson;

    @Title(title = "规则参数JSON")
    @Col(name = "threshold_json", dataType = "text")
    private String thresholdJson;

    @Title(title = "参与者选择器JSON")
    @Col(name = "owner_selector", dataType = "text")
    private String ownerSelector;

    @Title(title = "解决联动策略", description = "ANY/ALL/SELF")
    @Col(name = "resolve_policy", nullable = false, charMaxlength = 8)
    private String resolvePolicy = "ANY";

    @Title(title = "风暴抑制配置JSON", description = "{hitLimit,overflowPolicy,cooldownMinutes,inhibitedBy}")
    @Col(name = "storm_json", dataType = "text")
    private String stormJson;

    @Title(title = "主关联业务对象类型")
    @Col(name = "biz_type", charMaxlength = 32)
    private String bizType;

    @Title(title = "衔接互斥组")
    @Col(name = "mutual_exclusive_group", charMaxlength = 64)
    private String mutualExclusiveGroup;

    @Title(title = "处置流程模板")
    @Col(name = "flow_template", charMaxlength = 32)
    private String flowTemplate;

    @Title(title = "去处理跳转模板")
    @Col(name = "biz_url_template", charMaxlength = 256)
    private String bizUrlTemplate;

    @Title(title = "启用状态", description = "0未启用/1生效")
    @Col(name = "enable_status", nullable = false, dataType = "int")
    private int enableStatus = 0;

    @Title(title = "扫描抢占", description = "0空闲/1执行中")
    @Col(name = "scan_status", nullable = false, dataType = "int")
    private int scanStatus = 0;

    @Title(title = "本轮扫描开始时刻")
    @Col(name = "scan_started_at")
    private Date scanStartedAt;

    @Title(title = "执行节点标识")
    @Col(name = "scan_runner", charMaxlength = 64)
    private String scanRunner;

    @Title(title = "引擎版本", description = "脚本契约快照，空=v1")
    @Col(name = "engine_version", charMaxlength = 8)
    private String engineVersion;

    @Title(title = "口径版本")
    @Col(name = "version", nullable = false, dataType = "int")
    private int version = 1;

    @Title(title = "所属应用ID", description = "platform_app.id")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;
}
