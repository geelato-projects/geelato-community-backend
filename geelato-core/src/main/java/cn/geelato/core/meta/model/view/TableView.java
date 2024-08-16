package cn.geelato.core.meta.model.view;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.Locale;

/**
 * @author diabl
 */
@Setter
@Title(title = "实体视图关系")
@Entity(name = "platform_dev_view")
public class TableView extends BaseSortableEntity implements EntityEnableAble {

    private String appId;
    private String connectId;
    private String entityName;
    private String title;
    private String viewName;
    private String viewType;
    private String viewConstruct;
    private String viewColumn;
    private String description;
    private int linked;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "connect_id")
    @Title(title = "数据库连接id")
    public String getConnectId() {
        return connectId;
    }

    @Col(name = "entity_name")
    @Title(title = "实体名称")
    public String getEntityName() {
        return entityName;
    }

    @Col(name = "title")
    @Title(title = "视图名称")
    public String getTitle() {
        return title;
    }

    @Col(name = "view_name")
    @Title(title = "视图名称")
    public String getViewName() {
        return viewName;
    }

    @Col(name = "view_type")
    @Title(title = "视图类型")
    public String getViewType() {
        return viewType;
    }

    @Col(name = "view_construct")
    @Title(title = "视图语句")
    public String getViewConstruct() {
        return viewConstruct;
    }

    @Col(name = "view_column")
    @Title(title = "视图语句")
    public String getViewColumn() {
        return viewColumn;
    }

    @Col(name = "description")
    @Title(title = "补充描述")
    public String getDescription() {
        return description;
    }

    @Col(name = "linked")
    @Title(title = "已链接")
    public int getLinked() {
        return linked;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    @Override
    public int getEnableStatus() {
        return enableStatus;
    }

    @Override
    public void afterSet() {
        if (Strings.isNotBlank(this.viewName)) {
            this.setViewName(this.viewName.toLowerCase(Locale.ENGLISH));
        }
    }
}
