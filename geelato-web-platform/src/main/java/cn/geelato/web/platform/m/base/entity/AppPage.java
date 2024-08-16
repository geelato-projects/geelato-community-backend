package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

/**
 * @author itechgee@126.com
 * @date 2017/5/27.
 */
@Setter
@Entity(name = "platform_app_page", table = "platform_app_page")
@Title(title = "页面")
public class AppPage extends BaseSortableEntity {

    private String extendId;
    private String appId;
    private String type;

    private String title;
    private String code;
    private String sourceContent;
    private String previewContent;
    private String releaseContent;
    private String description;

    private int version;

    @Col(name = "app_id", nullable = true)
    @Title(title = "应用Id", description = "所属应用ID")
    public String getAppId() {
        return appId;
    }

    @Col(name = "extend_id", nullable = true)
    @Title(title = "扩展信息", description = "扩展id，如对应的叶子节点id")
    public String getExtendId() {
        return extendId;
    }

    @Col(name = "type", nullable = false)
    @Title(title = "类型", description = "如api|form|table")
    public String getType() {
        return type;
    }

    @Col(name = "title", nullable = true)
    @Title(title = "标题")
    public String getTitle() {
        return title;
    }

    @Col(name = "code", nullable = true)
    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    @Col(name = "source_content", nullable = false, dataType = "longText")
    @Title(title = "源文件内容")
    public String getSourceContent() {
        return sourceContent;
    }

    @Col(name = "preview_content", nullable = false, dataType = "longText")
    @Title(title = "预览的内容")
    public String getPreviewContent() {
        return previewContent;
    }

    @Col(name = "release_content", nullable = false, dataType = "longText")
    @Title(title = "发布的内容")
    public String getReleaseContent() {
        return releaseContent;
    }

    @Col(name = "version")
    @Title(title = "版本")
    public int getVersion() {
        return version;
    }

    @Title(title = "描述")
    public String getDescription() {
        return description;
    }
}
