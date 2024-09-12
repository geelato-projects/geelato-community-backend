package cn.geelato.web.platform.m.settings.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_area")
@Title(title = "区县")
public class Area extends BaseSortableEntity {
    @Title(title = "名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "省份")
    @Col(name = "city_id", nullable = false)
    private String cityId;
    @Title(title = "描述")
    private String description;
}
