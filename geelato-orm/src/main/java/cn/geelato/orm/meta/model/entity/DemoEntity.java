package cn.geelato.orm.meta.model.entity;

import org.geelato.core.meta.annotation.Col;
import org.geelato.core.meta.annotation.Entity;
import org.geelato.core.meta.annotation.Title;

/**
 * @author geelato
 * @date 2018/7/14
 */
@Entity(name = "platform_demo_entity", table = "platform_demo_entity")
@Title(title = "示例实体")
public class DemoEntity extends BaseSortableEntity {

    private String name;
    private String type;
    private String code;
    private String content;
    private String description;


    @Col(name = "name", nullable = true)
    @Title(title = "名称")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Col(name = "type", nullable = false)
    @Title(title = "类型", description = "如api|form|table")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Col(name = "code", nullable = true)
    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Col(name = "content", nullable = false, dataType = "text")
    @Title(title = "内容")
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Col(name = "description", nullable = true, charMaxlength = 1024)
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
