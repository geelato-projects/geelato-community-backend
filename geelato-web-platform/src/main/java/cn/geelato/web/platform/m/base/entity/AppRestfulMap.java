package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

/**
 * @author diabl
 */
@Setter
@Entity(name = "platform_app_r_restful")
@Title(title = "应用接口编码关系表")
public class AppRestfulMap extends BaseEntity {
    private String appId;
    private String appName;
    private String restfulId;
    private String restfulTitle;
    private String restfulKey;
    private String restfulAppId;
    private boolean approvalNeed = false;
    private String approvalStatus;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String description;

    @Col(name = "app_id")
    @Title(title = "申请应用主键")
    public String getAppId() {
        return appId;
    }

    @Col(name = "app_name")
    @Title(title = "申请应用名称")
    public String getAppName() {
        return appName;
    }

    @Col(name = "restful_id")
    @Title(title = "接口编排主键")
    public String getRestfulId() {
        return restfulId;
    }

    @Col(name = "restful_title")
    @Title(title = "接口编排标题")
    public String getRestfulTitle() {
        return restfulTitle;
    }

    @Col(name = "restful_key")
    @Title(title = "接口编排键名称")
    public String getRestfulKey() {
        return restfulKey;
    }

    @Col(name = "restful_app_id")
    @Title(title = "接口编排所属应用")
    public String getRestfulAppId() {
        return restfulAppId;
    }

    @Col(name = "approval_need")
    @Title(title = "是否需要审批")
    public boolean isApprovalNeed() {
        return approvalNeed;
    }

    @Col(name = "approval_status")
    @Title(title = "审批状态")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    @Col(name = "enable_status")
    @Title(title = "是否启用")
    public int getEnableStatus() {
        return enableStatus;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }
}
