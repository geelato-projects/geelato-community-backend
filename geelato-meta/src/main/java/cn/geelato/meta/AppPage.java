package cn.geelato.meta;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_app_page",catalog = "platform")
@Title(title = "页面")
public class AppPage extends BaseSortableEntity {

    @Title(title = "扩展信息", description = "扩展id，如对应的叶子节点id")
    @Col(name = "extend_id", nullable = true)
    private String extendId;
    @Title(title = "应用Id", description = "所属应用ID")
    @Col(name = "app_id", nullable = true)
    private String appId;
    @Title(title = "类型", description = "如api|form|table")
    private String type;
    @Title(title = "标题")
    private String title;
    @Title(title = "编码")
    private String code;
    @Title(title = "源文件内容")
    @Col(name = "source_content", nullable = false, dataType = "longText")
    private String sourceContent;
    @Title(title = "预览的内容")
    @Col(name = "preview_content", nullable = false, dataType = "longText")
    private String previewContent;
    @Title(title = "发布的内容")
    @Col(name = "release_content", nullable = false, dataType = "longText")
    private String releaseContent;
    @Title(title = "描述")
    private String description;
    @Title(title = "版本")
    private int version;
    @Title(title = "签入用户ID")
    @Col(name = "check_user_id", nullable = true)
    private String checkUserId;
    
    @Title(title = "签入用户名")
    @Col(name = "check_user_name", nullable = true)
    private String checkUserName;
    @Title(title = "签入状态", description = "取值：unchecked（未签出）、checkedOut（已签出）")
    @Col(name = "check_status", nullable = true)
    private String checkStatus;
    @Title(title = "签入时间")
    @Col(name = "check_at", nullable = true)
    private java.util.Date checkAt;
}
