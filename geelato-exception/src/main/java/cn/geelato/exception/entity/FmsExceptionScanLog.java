package cn.geelato.exception.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务异常预警中心-规则扫描日志 / 事件接入幂等留痕实体。
 *
 * <p>表名 platform_exception_scan_log，记录每轮扫描的候选/命中/建实例/续期/恢复/抑制计数与耗时；
 * {@code event_id} 唯一键兼作 CHANGE 事件接入（entity-event）的幂等去重键。
 *
 * <p>关联文档：bea/业务异常预警中心-数据表设计-2026-09-17.md §8
 */
@Getter
@Setter
@Entity(name = "platform_exception_scan_log", catalog = "geelato")
@Title(title = "异常扫描日志")
public class FmsExceptionScanLog extends BaseSortableEntity {

    @Title(title = "规则编码")
    @Col(name = "rule_code", nullable = false, charMaxlength = 32)
    private String ruleCode;

    @Title(title = "触发类型", description = "SCAN/RECHECK/TRIAL")
    @Col(name = "trigger_type", charMaxlength = 16)
    private String triggerType;

    @Title(title = "开始时刻")
    @Col(name = "start_at")
    private Date startAt;

    @Title(title = "结束时刻")
    @Col(name = "finish_at")
    private Date finishAt;

    @Title(title = "候选量")
    @Col(name = "candidate_count", nullable = false, dataType = "int")
    private int candidateCount = 0;

    @Title(title = "判定命中数")
    @Col(name = "hit_count", nullable = false, dataType = "int")
    private int hitCount = 0;

    @Title(title = "本轮新建实例数")
    @Col(name = "instance_created", nullable = false, dataType = "int")
    private int instanceCreated = 0;

    @Title(title = "本轮续期实例数")
    @Col(name = "instance_renewed", nullable = false, dataType = "int")
    private int instanceRenewed = 0;

    @Title(title = "本轮恢复实例数")
    @Col(name = "instance_recovered", nullable = false, dataType = "int")
    private int instanceRecovered = 0;

    @Title(title = "本轮抑制数")
    @Col(name = "suppressed_count", nullable = false, dataType = "int")
    private int suppressedCount = 0;

    @Title(title = "耗时毫秒")
    @Col(name = "cost_ms", nullable = false, dataType = "int")
    private int costMs = 0;

    @Title(title = "管道步骤明细JSON")
    @Col(name = "steps_json", dataType = "text")
    private String stepsJson;

    @Title(title = "失败留痕")
    @Col(name = "error", charMaxlength = 2000)
    private String error;

    @Title(title = "事件接入幂等键")
    @Col(name = "event_id", charMaxlength = 64)
    private String eventId;
}
