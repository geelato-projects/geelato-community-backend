package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;

/**
 * @author geelato
 */
@Entity(name = "platform_demo_sub_entity", table = "platform_demo_sub_entity")
@Title(title = "示例子实体")
public class DemoSubEntity extends BaseSortableEntity {

    private String demoEntityId;
    private String name;
    private String description;


    @Col(name = "demoEntityId", nullable = true)
    @Title(title = "示例实体Id")
    public String getDemoEntityId() {
        return demoEntityId;
    }

    public void setDemoEntityId(String demoEntityId) {
        this.demoEntityId = demoEntityId;
    }

    @Col(name = "name", nullable = true)
    @Title(title = "名称")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
