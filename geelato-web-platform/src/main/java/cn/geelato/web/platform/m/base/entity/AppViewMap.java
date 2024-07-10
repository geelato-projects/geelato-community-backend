package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 * @author diabl
 * @date 2024/4/17 16:24
 */
@Entity(name = "platform_app_r_view")
@Title(title = "应用视图授权关系表")
public class AppViewMap extends BaseEntity {
    private String appId;
    private String appName;
    private String tableName;
    private String viewId;
    private String viewName;
    private String viewTitle;
    private String viewAppId;
    private String permissionId;
    private String permissionName;
    private boolean approvalNeed = false;
    private String approvalStatus;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String description;

    @Title(title = "应用ID")
    @Col(name = "app_id", refTables = "platform_app", refColName = "platform_app.id")
    @ForeignKey(fTable = App.class)
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Title(title = "应用名称")
    @Col(name = "app_name", isRefColumn = true, refLocalCol = "appId", refColName = "platform_app.name")
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Title(title = "模型名称")
    @Col(name = "table_name")
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Title(title = "权限ID")
    @Col(name = "permission_id")
    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    @Title(title = "是否需要审批")
    @Col(name = "approval_need")
    public boolean isApprovalNeed() {
        return approvalNeed;
    }

    public void setApprovalNeed(boolean approvalNeed) {
        this.approvalNeed = approvalNeed;
    }

    @Title(title = "审批状态")
    @Col(name = "approval_status")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    @Title(title = "是否启用")
    @Col(name = "enable_status")
    public int getEnableStatus() {
        return enableStatus;
    }

    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    @Title(title = "是否启用")
    @Col(name = "permission_name")
    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    @Title(title = "描述")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Title(title = "视图Id")
    @Col(name = "view_id")
    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    @Title(title = "视图名称")
    @Col(name = "view_name")
    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Title(title = "视图标题")
    @Col(name = "view_title")
    public String getViewTitle() {
        return viewTitle;
    }

    public void setViewTitle(String viewTitle) {
        this.viewTitle = viewTitle;
    }

    @Title(title = "视图所属应用")
    @Col(name = "view_app_id")
    public String getViewAppId() {
        return viewAppId;
    }

    public void setViewAppId(String viewAppId) {
        this.viewAppId = viewAppId;
    }
}
