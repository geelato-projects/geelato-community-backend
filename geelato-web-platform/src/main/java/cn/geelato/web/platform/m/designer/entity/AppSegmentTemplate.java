package cn.geelato.web.platform.m.designer.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Setter
@Entity(name = "platform_app_segment", table = "platform_app_segment")
@Title(title = "页面片段模板")
public class AppSegmentTemplate extends BaseSortableEntity {

    private String extendId;
    private String type;
    private String title;
    private String code;
    private String htmlContent;
    private String jsonContent;
    private String thumbnail;
    private String originalPicture;
    private String description;

    @Col(name = "extend_id", nullable = true)
    @Title(title = "扩展信息", description = "扩展id，如对应的叶子节点id")
    public String getExtendId() {
        return extendId;
    }

    @Col(name = "type", nullable = false)
    @Title(title = "类型", description = "")
    public String getType() {
        return type;
    }

    @Col(name = "code", nullable = true)
    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    @Col(name = "title", nullable = false)
    @Title(title = "标题")
    public String getTitle() {
        return title;
    }

    @Col(name = "html_content", nullable = false, dataType = "longText")
    @Title(title = "HTML内容")
    public String getHtmlContent() {
        return htmlContent;
    }

    @Col(name = "json_content", nullable = false, dataType = "longText")
    @Title(title = "JSON内容")
    public String getJsonContent() {
        return jsonContent;
    }

    @Col(name = "thumbnail", nullable = false, dataType = "longText")
    @Title(title = "缩略图")
    public String getThumbnail() {
        return thumbnail;
    }

    @Col(name = "original_picture", nullable = false, dataType = "longText")
    @Title(title = "原图")
    public String getOriginalPicture() {
        return originalPicture;
    }

    @Title(title = "描述")
    public String getDescription() {
        return description;
    }
}
