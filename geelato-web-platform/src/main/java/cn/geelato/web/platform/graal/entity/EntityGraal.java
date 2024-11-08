package cn.geelato.web.platform.graal.entity;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 实体
 */
@Getter
@Setter
public class EntityGraal {
    @Title(title = "实体名称")
    private String entity;
    @Title(title = "@biz")
    private String pidName;
    @Title(title = "实体字段集合")
    private List<EntityField> fields;
    @Title(title = "实体查询条件集合")
    private List<EntityParams> params;
    @Title(title = "实体查询排序集合")
    private List<EntityOrder> order;
    @Title(title = "实体查询最大记录数")
    private Integer pageSize;
    @Title(title = "加载时机，false:调用时;true:初始化时")
    private Boolean immediate = false;
    @Title(title = "加载约束")
    private String triggerConstraints;
    @Title(title = "是否懒加载")
    private Boolean lazyLoad = false;
}
