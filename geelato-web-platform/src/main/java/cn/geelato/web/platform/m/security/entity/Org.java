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

    @Title(title = "名称")
    @Col(name = "name", nullable = false)
    private String name;

    @Title(title = "编码")
    @Col(name = "code")
    private String code;

    @Title(title = "上级组织")
    @Col(name = "pid")
    private String pid;

    @Title(title = "类型", description = "组织类型：department-部门，company-公司")
    @Col(name = "type")
    private String type;

    @Title(title = "类别", description = "组织类别：inside-内部，outside-外部，virtual-虚拟")
    @Col(name = "category")
    private String category;

    @Title(title = "状态", description = "0:停用|1:启用")
    @Col(name = "status")
    private int status;

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 1024)
    private String description;


}
