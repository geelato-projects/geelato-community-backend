package cn.geelato.exception.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务异常预警中心-异常实例实体（一人一实例，参与者实例模型）。
 *
 * <p>表名 platform_exception。{@code active_fingerprint} 在途唯一、终态置 NULL；
 * {@code source_key} 为同源组键 {rule_code}|{biz_type}|{biz_id}。
 *
 * <p>关联文档：bea/业务异常预警中心-数据表设计-2026-09-17.md §4
 */
@Getter
@Setter
@Entity(name = "platform_exception", catalog = "geelato")
@Title(title = "异常实例")
public class FmsException extends BaseSortableEntity {

    @Title(title = "规则编码")
    @Col(name = "rule_code", nullable = false, charMaxlength = 32)
    private String ruleCode;

    @Title(title = "规则名称")
    @Col(name = "rule_name", charMaxlength = 128)
    private String ruleName;

    @Title(title = "业务域编码")
    @Col(name = "domain_code", charMaxlength = 16)
    private String domainCode;

    @Title(title = "等级", description = "P0~P3")
    @Col(name = "level", charMaxlength = 2)
    private String level;

    @Title(title = "在途唯一指纹", description = "终态置 NULL")
    @Col(name = "active_fingerprint", charMaxlength = 160)
    private String activeFingerprint;

    @Title(title = "同源组键")
    @Col(name = "source_key", charMaxlength = 128)
    private String sourceKey;

    @Title(title = "处理人")
    @Col(name = "participant_id", charMaxlength = 64)
    private String participantId;

    @Title(title = "处理人名称")
    @Col(name = "participant_name", charMaxlength = 64)
    private String participantName;

    @Title(title = "处理人组织")
    @Col(name = "participant_org", charMaxlength = 64)
    private String participantOrg;

    @Title(title = "业务对象类型", description = "回溯坐标，脚本返回优先/规则兜底")
    @Col(name = "biz_type", charMaxlength = 32)
    private String bizType;

    @Title(title = "业务对象ID")
    @Col(name = "biz_id", charMaxlength = 64)
    private String bizId;

    @Title(title = "业务可读标识", description = "列表认对象/URL 模板 {bizLabel}")
    @Col(name = "biz_label", charMaxlength = 64)
    private String bizLabel;

    @Title(title = "状态", description = "OPEN/PROCESSING/RESOLVED/IGNORED/ESCALATED")
    @Col(name = "status", nullable = false, charMaxlength = 16)
    private String status = "OPEN";

    @Title(title = "当前步骤编码")
    @Col(name = "current_step_code", charMaxlength = 32)
    private String currentStepCode;

    @Title(title = "升级链位置", description = "0基础/1..n")
    @Col(name = "escalation_level", nullable = false, dataType = "int")
    private int escalationLevel = 0;

    @Title(title = "首次触发")
    @Col(name = "first_at")
    private Date firstAt;

    @Title(title = "最近命中续期")
    @Col(name = "last_seen_at")
    private Date lastSeenAt;

    @Title(title = "累计命中")
    @Col(name = "hit_count", nullable = false, dataType = "int")
    private int hitCount = 0;

    @Title(title = "锚点时长秒")
    @Col(name = "anchor_elapsed", dataType = "bigint")
    private Long anchorElapsed;

    @Title(title = "实例标题", description = "脚本组装，缺省兜底规则名")
    @Col(name = "title", charMaxlength = 512)
    private String title;

    @Title(title = "抑制维度键", description = "引用本规则的 inhibitedBy 各声明拼键 JSON 数组")
    @Col(name = "suppress_keys", dataType = "text")
    private String suppressKeys;

    @Title(title = "触发快照JSON")
    @Col(name = "detail_json", dataType = "text")
    private String detailJson;

    @Title(title = "关闭时刻")
    @Col(name = "resolved_at")
    private Date resolvedAt;

    @Title(title = "关闭来源", description = "AUTO/MANUAL/EVENT/GROUP")
    @Col(name = "resolved_type", charMaxlength = 16)
    private String resolvedType;

    @Title(title = "规则版本")
    @Col(name = "rule_version", dataType = "int")
    private Integer ruleVersion;

    @Title(title = "所属应用ID", description = "platform_app.id")
    @Col(name = "app_id", charMaxlength = 32)
    private String appId;
}
