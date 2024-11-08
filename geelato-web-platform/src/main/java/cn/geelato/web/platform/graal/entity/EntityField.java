package cn.geelato.web.platform.graal.entity;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体字段
 */
@Getter
@Setter
public class EntityField {
    @Title(title = "字段名")
    private String name;
    @Title(title = "字段标题")
    private String title;
    @Title(title = "字段别名")
    private String alias;
    @Title(title = "计算字段")
    private boolean isLocalComputeFiled = false;
    @Title(title = "字段值")
    private String value;
    @Title(title = "字段表达式")
    private String valueExpression;
}
