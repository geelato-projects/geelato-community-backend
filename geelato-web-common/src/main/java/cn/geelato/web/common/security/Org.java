package cn.geelato.web.common.security;


import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_org",catalog = "platform")
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
