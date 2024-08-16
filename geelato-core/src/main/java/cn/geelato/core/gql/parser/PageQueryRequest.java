package cn.geelato.core.gql.parser;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 分页查询参数
 * @date 2024/3/29 13:57
 */
@Getter
@Setter
public class PageQueryRequest {
    private int pageNum;
    private int pageSize;
    private String orderBy;
}
