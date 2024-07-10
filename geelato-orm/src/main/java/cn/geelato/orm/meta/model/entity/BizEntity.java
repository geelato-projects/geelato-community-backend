package cn.geelato.orm.meta.model.entity;

import org.geelato.core.meta.annotation.Col;
import org.geelato.core.meta.annotation.Title;

/**
 * 基础业务实体，增加了工作流信息
 */
public class BizEntity extends BaseEntity {

    // 工作流实例id
    private String procId;
    // 工作流状态编码
    private String procStatus;


    @Col(name = "proc_id", nullable = true)
    @Title(title = "流程实例ID")
    public String getProcId() {
        return procId;
    }

    public void setProcId(String procId) {
        this.procId = procId;
    }

    @Col(name = "proc_status", nullable = true)
    @Title(title = "流程状态")
    public String getProcStatus() {
        return procStatus;
    }

    public void setProcStatus(String procStatus) {
        this.procStatus = procStatus;
    }


    /***
     * 其它属性设置之后，调用。可用于通用的增删改查功能中，特别字段的生成
     */
    @Override
    public void afterSet() {

    }
}
