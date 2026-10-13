package cn.geelato.exception.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 业务异常预警中心-处置步骤实体。
 *
 * <p>表名 platform_exception_step，实例的处置步骤流转（认领/处理/终审/验证），支撑时间线与 MTTR。
 *
 * <p>关联文档：bea/业务异常预警中心-数据表设计-2026-09-17.md §5
 */
@Getter
@Setter
@Entity(name = "platform_exception_step", catalog = "geelato")
@Title(title = "异常处置步骤")
public class FmsExceptionStep extends BaseSortableEntity {

    @Title(title = "实例ID")
    @Col(name = "exception_id", nullable = false, charMaxlength = 32)
    private String exceptionId;

    @Title(title = "步骤编码", description = "CLAIM/HANDLE/INTERCEPT/REVIEW/VERIFY")
    @Col(name = "step_code", nullable = false, charMaxlength = 32)
    private String stepCode;

    @Title(title = "步骤名称")
    @Col(name = "step_name", charMaxlength = 64)
    private String stepName;

    @Title(title = "序号")
    @Col(name = "seq", nullable = false, dataType = "int")
    private int seq = 0;

    @Title(title = "状态", description = "PENDING/ACTIVE/DONE/TERMINATED")
    @Col(name = "status", nullable = false, charMaxlength = 16)
    private String status = "PENDING";

    @Title(title = "步骤参与者选择器JSON")
    @Col(name = "resolver", dataType = "text")
    private String resolver;

    @Title(title = "求解结果JSON")
    @Col(name = "participants", dataType = "text")
    private String participants;

    @Title(title = "指派时刻")
    @Col(name = "assigned_at")
    private Date assignedAt;

    @Title(title = "完成时刻")
    @Col(name = "done_at")
    private Date doneAt;

    @Title(title = "预计超时时刻")
    @Col(name = "timeout_at")
    private Date timeoutAt;

    @Title(title = "升级次数")
    @Col(name = "escalate_count", nullable = false, dataType = "int")
    private int escalateCount = 0;

    @Title(title = "完成动作", description = "CLAIMED/APPROVED/REJECTED/SKIPPED")
    @Col(name = "done_action", charMaxlength = 24)
    private String doneAction;
}
