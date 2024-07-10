package cn.geelato.web.platform.m.settings.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;

@Entity(name = "platform_area")
@Title(title = "区县")
public class Area extends BaseSortableEntity {
    private String name;
    private String code;
    private String cityId;
    private String description;

    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Title(title = "名称")
    @Col(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Title(title = "省份")
    @Col(name = "city_id", nullable = false)
    public String getProvinceId() {
        return cityId;
    }

    public void setProvinceId(String cityId) {
        this.cityId = cityId;
    }

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 1024)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
