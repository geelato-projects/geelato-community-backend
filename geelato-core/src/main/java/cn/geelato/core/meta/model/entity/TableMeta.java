package cn.geelato.core.meta.model.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author geemeta
 */
@Getter
@Setter
@Title(title = "实体信息")
@Entity(name = "platform_dev_table")
public class TableMeta extends BaseSortableEntity implements EntityEnableAble {
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "名称(中文)")
    private String title;
    @Title(title = "数据库连接id")
    @Col(name = "connect_id")
    private String connectId;
    @Col(name = "table_schema")
    @Title(title = "数据库schema")
    private String tableSchema;
    @Title(title = "表名", description = "与数据库中的表名一致")
    @Col(name = "table_name")
    private String tableName;
    @Title(title = "实体名称", description = "实体名称")
    @Col(name = "entity_name")
    private String entityName;
    @Title(title = "表格类型", description = "entity or view")
    @Col(name = "table_type")
    private String tableType;
    @Title(title = "备注")
    @Col(name = "table_comment")
    private String tableComment;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 1)
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "已链接")
    private int linked;
    @Title(title = "补充描述")
    private String description;
    @Title(title = "同步状态")
    private Boolean synced = false;
    @Title(title = "来源类型", description = "system:系统;creation:创建;")
    @Col(name = "source_type")
    private String sourceType;
    @Title(title = "数据打包策略：0 不打包，1 增量，2 全量")
    @Col(name = "pack_bus_data")
    private int packBusData = 0;
    @Title(title = "视图语句")
    @Col(name = "view_sql")
    private String viewSql;
    @Title(title = "跨应用")
    @Col(name = "across_app")
    private boolean acrossApp = false;
    @Title(title = "跨工作流")
    @Col(name = "across_workflow")
    private boolean acrossWorkflow = false;

    public TableMeta() {
    }

    public TableMeta(String tableName, String title, String entityName, String description) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.title = StringUtils.hasText(title) ? title : (StringUtils.isEmpty(tableName) ? entityName : tableName);
        this.tableComment = description;
        this.description = description;
    }

    public TableMeta(Map map) {
        this.appId = map.get("app_id") == null ? null : map.get("app_id").toString();
        this.title = map.get("title") == null ? null : map.get("title").toString();
        this.connectId = map.get("connect_id") == null ? null : map.get("connect_id").toString();
        this.tableSchema = map.get("table_schema") == null ? null : map.get("table_schema").toString();
        this.tableName = map.get("table_name") == null ? null : map.get("table_name").toString();
        this.entityName = map.get("entity_name") == null ? null : map.get("entity_name").toString();
        this.tableType = map.get("table_type") == null ? null : map.get("table_type").toString();
        this.tableComment = map.get("table_comment") == null ? null : map.get("table_comment").toString();
        Boolean enableStatus = map.get("enable_status") == null ? null : Boolean.parseBoolean(map.get("enable_status").toString());
        this.enableStatus = Boolean.TRUE.equals(enableStatus) ? 1 : 0;
        this.linked = map.get("linked") == null ? null : Integer.parseInt(map.get("linked").toString());
        this.description = map.get("description") == null ? null : map.get("description").toString();
        this.synced = map.get("synced") == null ? false : Boolean.parseBoolean(map.get("synced").toString());
        this.sourceType = map.get("source_type") == null ? null : map.get("source_type").toString();
        this.packBusData = map.get("pack_bus_data") == null ? 0 : Integer.parseInt(map.get("pack_bus_data").toString());
        this.viewSql = map.get("view_sql") == null ? null : map.get("view_sql").toString();
        this.acrossApp = map.get("across_app") == null ? false : Boolean.parseBoolean(map.get("across_app").toString());
        this.acrossWorkflow = map.get("across_workflow") == null ? false : Boolean.parseBoolean(map.get("across_workflow").toString());
    }
}
