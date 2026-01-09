package cn.geelato.core.mql.parser;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class PageQueryRequest {
    private int pageNum;
    private int pageSize;
    private String orderBy;
}
