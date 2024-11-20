package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Title(title = "应用数据模型检查表")
@Entity(name = "platform_dev_table_check")
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
}
