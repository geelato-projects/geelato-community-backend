package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 * @author diabl
 */
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

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Col(name = "app_name")
    @Title(title = "申请应用名称")
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Col(name = "restful_id")
    @Title(title = "接口编排主键")
    public String getRestfulId() {
        return restfulId;
    }

    public void setRestfulId(String restfulId) {
        this.restfulId = restfulId;
    }

    @Col(name = "restful_title")
    @Title(title = "接口编排标题")
    public String getRestfulTitle() {
        return restfulTitle;
    }

    public void setRestfulTitle(String restfulTitle) {
        this.restfulTitle = restfulTitle;
    }

    @Col(name = "restful_key")
    @Title(title = "接口编排键名称")
    public String getRestfulKey() {
        return restfulKey;
    }

    public void setRestfulKey(String restfulKey) {
        this.restfulKey = restfulKey;
    }

    @Col(name = "restful_app_id")
    @Title(title = "接口编排所属应用")
    public String getRestfulAppId() {
        return restfulAppId;
    }

    public void setRestfulAppId(String restfulAppId) {
        this.restfulAppId = restfulAppId;
    }

    @Col(name = "approval_need")
    @Title(title = "是否需要审批")
    public boolean isApprovalNeed() {
        return approvalNeed;
    }

    public void setApprovalNeed(boolean approvalNeed) {
        this.approvalNeed = approvalNeed;
    }

    @Col(name = "approval_status")
    @Title(title = "审批状态")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    @Col(name = "enable_status")
    @Title(title = "是否启用")
    public int getEnableStatus() {
        return enableStatus;
    }

    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
