package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_org")
@Title(title = "组织")
public class Org extends BaseSortableEntity {
    private String name;
    private String code;
    private String pid;
    private String type;
    private String category;
    private int status;
    private String description;
}
