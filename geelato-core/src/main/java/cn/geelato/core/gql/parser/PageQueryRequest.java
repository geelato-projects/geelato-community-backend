package cn.geelato.core.gql.parser;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 分页查询参数
 */
@Getter
@Setter
public class PageQueryRequest {
    private int pageNum;
    private int pageSize;
    private String orderBy;
}
