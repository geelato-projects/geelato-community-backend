package cn.geelato.core.meta.model.view;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.Locale;

/**
 * @author diabl
 */
@Getter
@Setter
@Title(title = "实体视图关系")
@Entity(name = "platform_dev_view")
public class TableView extends BaseSortableEntity implements EntityEnableAble {
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "数据库连接id")
    @Col(name = "connect_id")
    private String connectId;
    @Col(name = "entity_name")
    @Title(title = "实体名称")
    private String entityName;
    @Title(title = "视图名称")
    private String title;
    @Title(title = "视图名称")
    @Col(name = "view_name")
    private String viewName;
    @Title(title = "视图类型")
    @Col(name = "view_type")
    private String viewType;
    @Title(title = "视图语句")
    @Col(name = "view_construct")
    private String viewConstruct;
    @Title(title = "视图语句")
    @Col(name = "view_column")
    private String viewColumn;
    @Title(title = "补充描述")
    private String description;
    @Title(title = "已链接")
    private int linked;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Override
    public void afterSet() {
        if (Strings.isNotBlank(this.viewName)) {
            this.setViewName(this.viewName.toLowerCase(Locale.ENGLISH));
        }
    }
}
