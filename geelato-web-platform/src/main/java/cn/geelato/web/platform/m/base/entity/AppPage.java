package cn.geelato.web.platform.m.base.entity;

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
@Entity(name = "platform_app_page")
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
}
