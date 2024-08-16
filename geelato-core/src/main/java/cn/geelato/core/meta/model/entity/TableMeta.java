package cn.geelato.core.meta.model.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.utils.StringUtils;
import lombok.Setter;

import java.util.Map;

/**
 * @author geemeta
 */
@Setter
@Title(title = "实体信息")
@Entity(name = "platform_dev_table")
public class TableMeta extends BaseSortableEntity implements EntityEnableAble {

    private String appId;
    private String title;
    private String connectId;
    private String tableName;
    private String entityName;
    private String tableType;
    private String tableComment;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private int linked;
    private String description;
    private Boolean synced = false;
    private String sourceType;
    private int packBusData = 0;// 0 不打包，1 增量，2 全量

    private String viewSql;

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
        this.tableName = map.get("table_name") == null ? null : map.get("table_name").toString();
        this.entityName = map.get("entity_name") == null ? null : map.get("entity_name").toString();
        this.tableType = map.get("table_type") == null ? null : map.get("table_type").toString();
        this.tableComment = map.get("table_comment") == null ? null : map.get("table_comment").toString();
        Boolean enableStatus = map.get("enable_status") == null ? null : Boolean.parseBoolean(map.get("enable_status").toString());
        this.enableStatus = Boolean.TRUE.equals(enableStatus) ? 1 : 0;
        this.linked = map.get("linked") == null ? null : Integer.parseInt(map.get("linked").toString());
        this.description = map.get("description") == null ? null : map.get("description").toString();
        this.synced = map.get("synced") != null && Boolean.parseBoolean(map.get("synced").toString());
        this.synced = map.get("synced") == null ? false : Boolean.parseBoolean(map.get("synced").toString());
        this.sourceType = map.get("source_type") == null ? null : map.get("source_type").toString();
        this.packBusData = map.get("pack_bus_data") == null ? 0 : Integer.parseInt(map.get("pack_bus_data").toString());
        this.viewSql = map.get("view_sql") == null ? null : map.get("view_sql").toString();
    }

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "table_name")
    @Title(title = "表名", description = "与数据库中的表名一致")
    public String getTableName() {
        return tableName;
    }

    @Col(name = "entity_name")
    @Title(title = "实体名称", description = "实体名称")
    public String getEntityName() {
        return entityName;
    }

    @Col(name = "connect_id")
    @Title(title = "数据库连接id")
    public String getConnectId() {
        return connectId;
    }

    @Col(name = "table_type")
    @Title(title = "表格类型", description = "entity or view")
    public String getTableType() {
        return tableType;
    }

    @Col(name = "table_comment")
    @Title(title = "备注")
    public String getTableComment() {
        return tableComment;
    }

    @Col(name = "title")
    @Title(title = "名称(中文)")
    public String getTitle() {
        return title;
    }

    @Col(name = "linked")
    @Title(title = "已链接")
    public int getLinked() {
        return linked;
    }

    @Col(name = "description")
    @Title(title = "补充描述")
    public String getDescription() {
        return description;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 1)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Col(name = "view_sql")
    @Title(title = "视图语句")
    public String getViewSql() {
        return viewSql;
    }

    @Col(name = "synced")
    @Title(title = "同步状态")
    public Boolean getSynced() {
        return synced;
    }

    @Col(name = "source_type")
    @Title(title = "来源类型", description = "system:系统;creation:创建;")
    public String getSourceType() {
        return sourceType;
    }

    @Col(name = "pack_bus_data")
    @Title(title = "数据打包策略：0 不打包，1 增量，2 全量")
    public int getPackBusData() {
        return packBusData;
    }
}
