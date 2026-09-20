package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询参数（安全契约读侧）。
 * <p>
 * pageNum 从 1 起；pageSize 会被钳制到 [1, {@link #MAX_PAGE_SIZE}]。
 */
@Getter
@Setter
public class PageParam {

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 500;

    private int pageNum = DEFAULT_PAGE_NUM;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public static PageParam of(int pageNum, int pageSize) {
        PageParam param = new PageParam();
        param.setPageNum(Math.max(pageNum, DEFAULT_PAGE_NUM));
        param.setPageSize(Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        return param;
    }

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
