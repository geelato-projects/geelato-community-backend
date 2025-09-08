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
    @Title(title = "组织名称")
    private String name;
    @Title(title = "组织编码")
    private String code;
    @Title(title = "父组织编码")
    private String pid;
    @Title(title = "组织类型")
    private String type;
    @Title(title = "组织分类")
    private String category;
    @Title(title = "组织状态")
    private int status;
    @Title(title = "组织描述")
    private String description;
}
