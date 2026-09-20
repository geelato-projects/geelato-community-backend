package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果（安全契约读侧）。
 */
@Getter
@Setter
public class Page<T> {

    // 当前页数据
    private List<T> items;
    // 总条数
    private long total;
    private int pageNum;
    private int pageSize;
    // 是否还有下一页
    private boolean hasMore;

    public static <T> Page<T> of(List<T> items, long total, int pageNum, int pageSize, boolean hasMore) {
        Page<T> page = new Page<T>();
        page.setItems(items == null ? Collections.emptyList() : items);
        page.setTotal(total);
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        page.setHasMore(hasMore);
        return page;
    }

    public static <T> Page<T> empty(PageParam pageParam) {
        PageParam param = pageParam == null ? PageParam.of(PageParam.DEFAULT_PAGE_NUM, PageParam.DEFAULT_PAGE_SIZE) : pageParam;
        return of(Collections.emptyList(), 0, param.getPageNum(), param.getPageSize(), false);
    }
}
