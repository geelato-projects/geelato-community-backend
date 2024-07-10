package cn.geelato.orm.meta.model.entity;

import org.geelato.core.constants.ColumnDefault;
import org.geelato.core.meta.annotation.Col;
import org.geelato.core.meta.annotation.Entity;
import org.geelato.core.meta.annotation.Title;

import java.util.Map;

/**
 * @author liuwq
 * @Description 实体外键关系
 * @Date 2020/3/20 14:42
 */
@Title(title = "实体外键关系")
@Entity(name = "platform_dev_table_foreign")
public class TableForeign extends BaseSortableEntity implements EntityEnableAble {

    private String appId;
    private String mainTableSchema;
    private String mainTableId;
    private String mainTable;
    private String mainTableCol;
    private String foreignTableSchema;
    private String foreignTableId;
    private String foreignTable;
    private String foreignTableCol;
    private String description;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String deleteAction = ColumnDefault.FOREIGN_ACTION_VALUE;// 删除时
    private String updateAction = ColumnDefault.FOREIGN_ACTION_VALUE;// 更新时

    public TableForeign() {
    }

    public TableForeign(Map map) {
        this.mainTable = map.get("main_table") == null ? null : map.get("main_table").toString();
        this.mainTableCol = map.get("main_table_col") == null ? null : map.get("main_table_col").toString();
        this.foreignTable = map.get("foreign_table") == null ? null : map.get("foreign_table").toString();
        this.foreignTableCol = map.get("foreign_table_col") == null ? null : map.get("foreign_table_col").toString();
        Boolean enableStatus = map.get("enable_status") == null ? null : Boolean.parseBoolean(map.get("enable_status").toString());
        this.enableStatus = Boolean.TRUE.equals(enableStatus) ? 1 : 0;
        this.description = map.get("description") == null ? null : map.get("description").toString();
        this.deleteAction = map.get("delete_action") == null ? ColumnDefault.FOREIGN_ACTION_VALUE : map.get("delete_action").toString();
        this.updateAction = map.get("update_action") == null ? ColumnDefault.FOREIGN_ACTION_VALUE : map.get("update_action").toString();
    }

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Col(name = "main_table")
    @Title(title = "主表表名")
    public String getMainTable() {
        return mainTable;
    }

    public void setMainTable(String mainTable) {
        this.mainTable = mainTable;
    }

    @Col(name = "main_table_col")
    @Title(title = "主表表名字段")
    public String getMainTableCol() {
        return mainTableCol;
    }

    public void setMainTableCol(String mainTableCol) {
        this.mainTableCol = mainTableCol;
    }

    @Col(name = "foreign_table")
    @Title(title = "外键关联表表名")
    public String getForeignTable() {
        return foreignTable;
    }

    public void setForeignTable(String foreignTable) {
        this.foreignTable = foreignTable;
    }

    @Col(name = "foreign_table_col")
    @Title(title = "外键关联表字段")
    public String getForeignTableCol() {
        return foreignTableCol;
    }

    public void setForeignTableCol(String foreignTableCol) {
        this.foreignTableCol = foreignTableCol;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    /**
     */
    @Override
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

    @Col(name = "delete_action")
    @Title(title = "删除时触发操作")
    public String getDeleteAction() {
        return deleteAction;
    }

    public void setDeleteAction(String deleteAction) {
        this.deleteAction = deleteAction;
    }

    @Col(name = "update_action")
    @Title(title = "更新时触发操作")
    public String getUpdateAction() {
        return updateAction;
    }

    public void setUpdateAction(String updateAction) {
        this.updateAction = updateAction;
    }

    @Col(name = "main_table_schema")
    public String getMainTableSchema() {
        return mainTableSchema;
    }

    public void setMainTableSchema(String mainTableSchema) {
        this.mainTableSchema = mainTableSchema;
    }

    @Col(name = "main_table_id")
    public String getMainTableId() {
        return mainTableId;
    }

    public void setMainTableId(String mainTableId) {
        this.mainTableId = mainTableId;
    }

    @Col(name = "foreign_table_schema")
    public String getForeignTableSchema() {
        return foreignTableSchema;
    }

    public void setForeignTableSchema(String foreignTableSchema) {
        this.foreignTableSchema = foreignTableSchema;
    }

    @Col(name = "foreign_table_id")
    public String getForeignTableId() {
        return foreignTableId;
    }

    public void setForeignTableId(String foreignTableId) {
        this.foreignTableId = foreignTableId;
    }
}
