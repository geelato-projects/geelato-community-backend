package cn.geelato.alert.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务告警中心-静默规则实体（维度匹配 + 时间窗）。
 *
 * <p>表名 platform_alert_silence，按 alertCodes/ruleCodes/customerIds 维度压制告警；
 * ONCE 用 start_at/end_at 绝对窗，RECURRING 用 time_window 周期窗。
 *
 * <p>关联文档：bea/业务告警中心-数据表设计-2026-09-22.md §6
 */
@Getter
@Setter
@Entity(name = "platform_alert_silence", catalog = "geelato")
@Title(title = "告警静默规则")
public class FmsAlertSilence extends BaseSortableEntity {

    @Title(title = "静默名称")
    @Col(name = "silence_name", nullable = false, charMaxlength = 128)
    private String silenceName;

    @Title(title = "压制维度JSON", description = "{alertCodes,ruleCodes,customerIds}")
    @Col(name = "match_json", dataType = "text")
    private String matchJson;

    @Title(title = "窗口类型", description = "ONCE/RECURRING")
    @Col(name = "window_type", nullable = false, charMaxlength = 16)
    private String windowType = "ONCE";

    @Title(title = "起", description = "ONCE 起 / RECURRING 生效总区间起")
    @Col(name = "start_at")
    private Date startAt;

    @Title(title = "止", description = "ONCE 止 / RECURRING 生效总区间止")
    @Col(name = "end_at")
    private Date endAt;

    @Title(title = "周期窗JSON", description = "RECURRING {days,startTime,endTime}")
    @Col(name = "time_window", dataType = "text")
    private String timeWindow;

    @Title(title = "静默原因")
    @Col(name = "reason", nullable = false, charMaxlength = 256)
    private String reason;

    @Title(title = "恢复通知豁免", description = "1不受压制")
    @Col(name = "allow_resolve", nullable = false, dataType = "int")
    private int allowResolve = 1;

    @Title(title = "状态", description = "ACTIVE/EXPIRED/REVOKED")
    @Col(name = "status", nullable = false, charMaxlength = 16)
    private String status = "ACTIVE";
}
