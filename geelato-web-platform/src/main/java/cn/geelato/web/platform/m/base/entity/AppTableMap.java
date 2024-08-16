package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.TableMeta;
import lombok.Setter;

/**
 * @author diabl
 * @date 2024/4/17 16:24
 */
@Setter
@Entity(name = "platform_app_r_table")
@Title(title = "应用模型授权关系表")
public class AppTableMap extends BaseEntity {
    private String appId;
    private String appName;
    private String tableId;
    private String tableName;
    private String tableTitle;
    private String tableAppId;
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

    @Title(title = "应用名称")
    @Col(name = "app_name", isRefColumn = true, refLocalCol = "appId", refColName = "platform_app.name")
    public String getAppName() {
        return appName;
    }

    @Title(title = "模型ID")
    @Col(name = "table_id", refTables = "platform_dev_table", refColName = "platform_dev_table.id")
    @ForeignKey(fTable = TableMeta.class)
    public String getTableId() {
        return tableId;
    }

    @Title(title = "模型名称")
    @Col(name = "table_name")
    public String getTableName() {
        return tableName;
    }

    @Title(title = "权限ID")
    @Col(name = "permission_id")
    public String getPermissionId() {
        return permissionId;
    }

    @Title(title = "是否需要审批")
    @Col(name = "approval_need")
    public boolean isApprovalNeed() {
        return approvalNeed;
    }

    @Title(title = "审批状态")
    @Col(name = "approval_status")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    @Title(title = "是否启用")
    @Col(name = "enable_status")
    public int getEnableStatus() {
        return enableStatus;
    }

    @Title(title = "是否启用")
    @Col(name = "table_title")
    public String getTableTitle() {
        return tableTitle;
    }

    @Title(title = "是否启用")
    @Col(name = "permission_name")
    public String getPermissionName() {
        return permissionName;
    }

    @Title(title = "描述")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    @Title(title = "模型所属应用ID")
    @Col(name = "table_app_id")
    public String getTableAppId() {
        return tableAppId;
    }
}
