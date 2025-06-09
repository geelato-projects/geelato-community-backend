package cn.geelato.orm.executor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询结果类
 * @param <T> 数据类型
 */
@Getter
public class PageResult<T> extends Result<T> {
    @Setter
    private int pageNum;            // 当前页码
    private int pageSize;           // 每页大小
    private int totalPages;         // 总页数

    public PageResult() {
        super();
    }
    public PageResult(List<T> data, long total, int pageNum, int pageSize) {
        super(data, total);
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
    

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "data=" + data +
                ", total=" + total +
                ", pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", totalPages=" + totalPages +
                '}';
    }
}