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
import java.util.Map;

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

    public TableView() {
    }

    /**
     * 从 platform_dev_view 行 Map 构造。
     * <p>仅做字段映射，不调用 {@link #afterSet()}，与原 Map 装载路径一致（避免 viewName 被二次 lowercase）。</p>
     */
    public TableView(Map<String, Object> map) {
        this.appId = map.get("app_id") == null ? null : map.get("app_id").toString();
        this.connectId = map.get("connect_id") == null ? null : map.get("connect_id").toString();
        this.entityName = map.get("entity_name") == null ? null : map.get("entity_name").toString();
        this.title = map.get("title") == null ? null : map.get("title").toString();
        this.viewName = map.get("view_name") == null ? null : map.get("view_name").toString();
        this.viewType = map.get("view_type") == null ? null : map.get("view_type").toString();
        this.viewConstruct = map.get("view_construct") == null ? null : map.get("view_construct").toString();
        this.viewColumn = map.get("view_column") == null ? null : map.get("view_column").toString();
        this.description = map.get("description") == null ? null : map.get("description").toString();
        this.linked = parseTinyint(map.get("linked"), 0);
        this.enableStatus = parseTinyint(map.get("enable_status"), ColumnDefault.ENABLE_STATUS_VALUE);
        this.setId(map.get("id") == null ? null : map.get("id").toString());
        this.setDelStatus(parseTinyint(map.get("del_status"), 0));
    }

    private static int parseTinyint(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        String s = value.toString();
        if ("true".equalsIgnoreCase(s)) {
            return 1;
        }
        if ("false".equalsIgnoreCase(s)) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
