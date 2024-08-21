package cn.geelato.web.platform.m.designer.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_app_segment", table = "platform_app_segment")
@Title(title = "页面片段模板")
public class AppSegmentTemplate extends BaseSortableEntity {

    @Col(name = "extend_id", nullable = true)
    @Title(title = "扩展信息", description = "扩展id，如对应的叶子节点id")
    private String extendId;
    private String type;
    private String title;
    private String code;
    @Col(name = "html_content", nullable = false, dataType = "longText")
    @Title(title = "HTML内容")
    private String htmlContent;
    @Col(name = "json_content", nullable = false, dataType = "longText")
    @Title(title = "JSON内容")
    private String jsonContent;
    private String thumbnail;

    @Col(name = "original_picture", nullable = false, dataType = "longText")
    @Title(title = "原图")
    private String originalPicture;
    private String description;


}
