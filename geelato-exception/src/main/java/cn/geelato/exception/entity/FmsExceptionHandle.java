package cn.geelato.exception.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务异常预警中心-处理轨迹流水实体（只追加，时间线）。
 *
 * <p>表名 platform_exception_handle，记录实例每一步动作（创建/认领/处理/终审/忽略/升级/自动恢复等），
 * 保存变更前/后状态，供详情页时间线回放。
 *
 * <p>关联文档：bea/业务异常预警中心-数据表设计-2026-09-17.md §6
 */
@Getter
@Setter
@Entity(name = "platform_exception_handle", catalog = "geelato")
@Title(title = "异常处理轨迹")
public class FmsExceptionHandle extends BaseSortableEntity {

    @Title(title = "实例ID")
    @Col(name = "exception_id", nullable = false, charMaxlength = 32)
    private String exceptionId;

    @Title(title = "步骤ID")
    @Col(name = "step_id", charMaxlength = 32)
    private String stepId;

    @Title(title = "动作", description = "CREATE/CLAIM/HANDLE_NOTE/RESOLVE/IGNORE/AUTO_RECOVER/ESCALATE/ESCALATED/ACK/GROUP_CLOSE/STEP_DONE")
    @Col(name = "action", nullable = false, charMaxlength = 24)
    private String action;

    @Title(title = "操作者类型", description = "SYSTEM/USER")
    @Col(name = "operator_type", charMaxlength = 16)
    private String operatorType;

    @Title(title = "操作人ID")
    @Col(name = "operator_id", charMaxlength = 64)
    private String operatorId;

    @Title(title = "操作人名称")
    @Col(name = "operator_name", charMaxlength = 64)
    private String operatorName;

    @Title(title = "变更前状态")
    @Col(name = "before_status", charMaxlength = 16)
    private String beforeStatus;

    @Title(title = "变更后状态")
    @Col(name = "after_status", charMaxlength = 16)
    private String afterStatus;

    @Title(title = "忽略原因码", description = "IGNORE 必填")
    @Col(name = "reason_code", charMaxlength = 32)
    private String reasonCode;

    @Title(title = "处理备注")
    @Col(name = "remark", charMaxlength = 1024)
    private String remark;
}
