package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.TableMeta;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_app_r_table",catalog = "platform")
@Title(title = "应用模型授权关系表")
public class AppTableMap extends BaseEntity {
    @Title(title = "应用ID")
    @ForeignKey(fTable = App.class)
    @Col(name = "app_id", refTables = "platform_app", refColName = "platform_app.id")
    private String appId;
    @Title(title = "应用名称")
    @Col(name = "app_name", isRefColumn = true, refLocalCol = "appId", refColName = "platform_app.name")
    private String appName;
    @Title(title = "模型ID")
    @ForeignKey(fTable = TableMeta.class)
    @Col(name = "table_id", refTables = "platform_dev_table", refColName = "platform_dev_table.id")
    private String tableId;
    @Title(title = "模型名称")
    @Col(name = "table_name")
    private String tableName;
    @Title(title = "模型标题")
    @Col(name = "table_title")
    private String tableTitle;
    @Title(title = "模型所属应用ID")
    @Col(name = "table_app_id")
    private String tableAppId;
    @Title(title = "权限ID")
    @Col(name = "permission_id")
    private String permissionId;
    @Title(title = "权限名称")
    @Col(name = "permission_name")
    private String permissionName;
    @Title(title = "是否需要审批")
    @Col(name = "approval_need")
    private boolean approvalNeed = false;
    @Title(title = "审批状态")
    @Col(name = "approval_status")
    private String approvalStatus;
    @Title(title = "是否启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "描述")
    private String description;
}
