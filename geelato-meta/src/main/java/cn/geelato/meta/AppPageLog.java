package cn.geelato.meta;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_app_page_log",catalog = "platform")
@Title(title = "页面操作日志")
public class AppPageLog extends BaseEntity {

    @Title(title = "应用Id", description = "所属应用ID")
    @Col(name = "app_id", nullable = true)
    private String appId;
    
    @Title(title = "页面Id", description = "关联的页面ID")
    @Col(name = "page_id", nullable = true)
    private String pageId;
    
    @Title(title = "编码")
    private String code;
    
    @Title(title = "标题")
    private String label;

    @Title(title = "版本")
    private int version;
    
    @Title(title = "扩展信息", description = "扩展id，如对应的叶子节点id")
    @Col(name = "extend_id", nullable = true)
    private String extendId;
    
    @Title(title = "描述")
    private String description;
    
    @Title(title = "源文件内容")
    @Col(name = "source_content", nullable = false, dataType = "longText")
    private String sourceContent;
}