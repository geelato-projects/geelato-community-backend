package cn.geelato.web.platform.graal.entity;

import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询排序
 */
@Getter
@Setter
public class EntityOrder {
    @Title(title = "字段")
    private String field;
    @Title(title = "排序方式,+:升序，-：降序")
    private String order;
}
