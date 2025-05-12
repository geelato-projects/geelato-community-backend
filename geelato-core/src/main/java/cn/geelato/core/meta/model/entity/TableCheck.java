package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Title(title = "应用数据模型检查表")
@Entity(name = "platform_dev_table_check",catalog = "platform")
public class TableCheck extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "标题")
    private String title;
    @Title(title = "编码")
    private String code;
    @Title(title = "类型")
    private String type;
    @Title(title = "检查条件")
    @Col(name = "check_clause")
    private String checkClause;
    @Title(title = "连接Id")
    @Col(name = "connect_id")
    private String connectId;
    @Title(title = "数据库名称")
    @Col(name = "table_schema")
    private String tableSchema;
    @Title(title = "表Id")
    @Col(name = "table_id")
    private String tableId;
    @Title(title = "表名称")
    @Col(name = "table_name")
    private String tableName;
    @Title(title = "列Id")
    @Col(name = "column_id")
    private String columnId;
    @Title(title = "列名称")
    @Col(name = "column_name")
    private String columnName;
    private String description;
    @Title(title = "启用状态")
    @Col(name = "enable_status")
    private int enableStatus;
    @Title(title = "同步状态")
    private Boolean synced = false;

    public TableCheck() {
    }

    public TableCheck(Map<String, Object> map) {
        this.appId = map.get("app_id") == null ? null : map.get("app_id").toString();
        this.title = map.get("title") == null ? null : map.get("title").toString();
        this.code = map.get("code") == null ? null : map.get("code").toString();
        this.type = map.get("type") == null ? null : map.get("type").toString();
        this.checkClause = map.get("check_clause") == null ? null : map.get("check_clause").toString();
        this.connectId = map.get("connect_id") == null ? null : map.get("connect_id").toString();
        this.tableSchema = map.get("table_schema") == null ? null : map.get("table_schema").toString();
        this.tableId = map.get("table_id") == null ? null : map.get("table_id").toString();
        this.tableName = map.get("table_name") == null ? null : map.get("table_name").toString();
        this.columnId = map.get("column_id") == null ? null : map.get("column_id").toString();
        this.columnName = map.get("column_name") == null ? null : map.get("column_name").toString();
        this.description = map.get("description") == null ? null : map.get("description").toString();
        this.enableStatus = map.get("enable_status") == null ? 0 : Integer.parseInt(map.get("enable_status").toString());
        Integer synced = map.get("synced") == null ? 0 : Integer.parseInt(map.get("synced").toString());
        this.synced = synced.intValue() == 1 ? true : false;
    }
}
