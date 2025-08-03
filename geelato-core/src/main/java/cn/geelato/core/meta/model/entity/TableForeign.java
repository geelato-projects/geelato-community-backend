package cn.geelato.core.meta.model.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;


@Getter
@Setter
@Title(title = "实体外键关系")
@Entity(name = "platform_dev_table_foreign",catalog = "platform")
public class TableForeign extends BaseSortableEntity implements EntityEnableAble {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;

    @Col(name = "main_table_schema")
    private String mainTableSchema;

    @Col(name = "main_table_id")
    private String mainTableId;

    @Col(name = "main_table")
    @Title(title = "主表表名")
    private String mainTable;
    @Col(name = "main_table_col")
    @Title(title = "主表表名字段")
    private String mainTableCol;

    @Col(name = "foreign_table_schema")
    private String foreignTableSchema;

    @Col(name = "foreign_table_id")
    private String foreignTableId;

    @Col(name = "foreign_table")
    @Title(title = "外键关联表表名")
    private String foreignTable;

    @Col(name = "foreign_table_col")
    @Title(title = "外键关联表字段")
    private String foreignTableCol;

    @Col(name = "description")
    @Title(title = "描述")
    private String description;

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Col(name = "delete_action")
    @Title(title = "删除时触发操作")
    private String deleteAction = ColumnDefault.FOREIGN_ACTION_VALUE;// 删除时

    @Col(name = "update_action")
    @Title(title = "更新时触发操作")
    private String updateAction = ColumnDefault.FOREIGN_ACTION_VALUE;// 更新时

    public TableForeign() {
    }

    public TableForeign(Map<String,Object> map) {
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
}
