package cn.geelato.web.platform.graal.entity;

import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体查询条件
 */
@Getter
@Setter
public class EntityParams {
    @Title(title = "分组名称")
    private String groupName;
    @Title(title = "字段名")
    private String title;
    @Title(title = "比较类型")
    private String cop;
    @Title(title = "值表达式")
    private String valueExpression;
}
