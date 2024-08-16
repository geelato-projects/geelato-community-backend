package cn.geelato.web.platform.m.settings.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

@Setter
public class BaseConfig extends BaseSortableEntity {
    private String name;
    private String code;
    private String value;
    private String description;

    public BaseConfig() {
    }

    @Col(name = "name", unique = true)
    @Title(title = "参数名称")
    public String getName() {
        return name;
    }

    @Col(name = "code", unique = true)
    @Title(title = "参数编码", description = "如menu，表示")
    public String getCode() {
        return code;
    }

    @Col(name = "value", nullable = false, dataType = "Text")
    @Title(title = "参数值")
    public String getValue() {
        return value;
    }

    @Col(name = "description", charMaxlength = 5120)
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }
}
