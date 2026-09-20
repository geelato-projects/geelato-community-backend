package cn.geelato.search.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索结果：命中文档 id 集合与截断标志。
 *
 * <p>truncated=true 表示命中数超过 maxIds 上限，ids 为前 maxIds 个。
 * 语义上截断结果不可用于等价路由（会漏行），调用方必须放弃路由回退 SQL 兜底——
 * 引擎如实标记，不做静默截断。
 */
public class SearchResult {

    private final List<String> ids;
    private final boolean truncated;

    public SearchResult(List<String> ids, boolean truncated) {
        this.ids = ids == null ? new ArrayList<>() : ids;
        this.truncated = truncated;
    }

    public static SearchResult empty() {
        return new SearchResult(List.of(), false);
    }

    public List<String> getIds() {
        return ids;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
