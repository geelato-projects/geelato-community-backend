package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 基础业务实体，增加了工作流信息
 */
@Getter
@Setter
public class ProcessEntity extends BaseEntity {

    // 工作流实例id
    @Col(name = "proc_id", nullable = true)
    @Title(title = "流程实例ID")
    private String procId;

    // 工作流状态编码
    @Col(name = "proc_status", nullable = true)
    @Title(title = "流程状态")
    private String procStatus;

}
