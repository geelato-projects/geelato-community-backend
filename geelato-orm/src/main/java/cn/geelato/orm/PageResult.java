package cn.geelato.orm;

import cn.geelato.lang.api.ApiPagedResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果类，实现MyBatis-Plus的IPage接口
 * 用于将MyBatis-Plus的分页数据转换为ApiPagedResult格式
 * 
 * @author geemeta
 */
@Getter
public class PageResult<E> implements IPage<E> {

    /**
     * 查询数据列表
     */
    private List<E> records = Collections.emptyList();

    /**
     * 总条数
     */
    private long total = 0;

    /**
     * 每页显示条数，默认 10
     */
    private long size = 10;

    /**
     * 当前页
     */
    private long current = 1;

    /**
     * 排序字段信息
     */
    private List<OrderItem> orders = Collections.emptyList();

    /**
     * 自动优化 COUNT SQL
     */
    private boolean optimizeCountSql = true;

    /**
     * 是否进行 count 查询
     */
    private boolean searchCount = true;

    /**
     * 计数 ID
     */
    private String countId;

    /**
     * 最大限制数量
     */
    private Long maxLimit;

    /**
     * 默认构造函数
     */
    public PageResult() {
    }

    /**
     * 构造函数
     *
     * @param current 当前页
     * @param size    每页显示条数
     */
    public PageResult(long current, long size) {
        this(current, size, 0);
    }

    /**
     * 构造函数
     *
     * @param current 当前页
     * @param size    每页显示条数
     * @param total   总条数
     */
    public PageResult(long current, long size, long total) {
        this(current, size, total, true);
    }

    /**
     * 构造函数
     *
     * @param current     当前页
     * @param size        每页显示条数
     * @param searchCount 是否进行count查询
     */
    public PageResult(long current, long size, boolean searchCount) {
        this(current, size, 0, searchCount);
    }

    /**
     * 构造函数
     *
     * @param current     当前页
     * @param size        每页显示条数
     * @param total       总条数
     * @param searchCount 是否进行count查询
     */
    public PageResult(long current, long size, long total, boolean searchCount) {
        if (current > 1) {
            this.current = current;
        }
        this.size = size;
        this.total = total;
        this.searchCount = searchCount;
    }

    /**
     * 转换为ApiPagedResult
     *
     * @return ApiPagedResult对象
     */
    public ApiPagedResult<List<E>> toApiPagedResult() {
        return ApiPagedResult.success(
                this.records,
                this.current,
                (int) this.size,
                this.records != null ? this.records.size() : 0,
                this.total
        );
    }

    /**
     * 转换为ApiPagedResult，带自定义消息
     *
     * @param message 自定义消息
     * @return ApiPagedResult对象
     */
    public ApiPagedResult<List<E>> toApiPagedResult(String message) {
        return ApiPagedResult.success(
                this.records,
                this.current,
                (int) this.size,
                this.records != null ? this.records.size() : 0,
                this.total,
                message
        );
    }

    /**
     * 静态方法：从IPage对象创建PageResult
     *
     * @param page IPage对象
     * @param <T>  数据类型
     * @return PageResult对象
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> pageResult = new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.searchCount());
        pageResult.setRecords(page.getRecords());
        pageResult.setOrders(page.orders());
        pageResult.setOptimizeCountSql(page.optimizeCountSql());
        pageResult.setCountId(page.countId());
        pageResult.setMaxLimit(page.maxLimit());
        return pageResult;
    }

    /**
     * 静态方法：创建空的PageResult
     *
     * @param <T> 数据类型
     * @return 空的PageResult对象
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(1, 10, 0, false);
    }

    // ========== IPage接口方法实现 ==========

    @Override
    public List<OrderItem> orders() {
        return this.orders;
    }

    @Override
    public boolean optimizeCountSql() {
        return this.optimizeCountSql;
    }

    @Override
    public boolean searchCount() {
        return this.searchCount;
    }

    @Override
    public String countId() {
        return this.countId;
    }

    @Override
    public Long maxLimit() {
        return this.maxLimit;
    }

    @Override
    public IPage<E> setRecords(List<E> records) {
        this.records = records;
        return this;
    }

    @Override
    public IPage<E> setTotal(long total) {
        this.total = total;
        return this;
    }

    @Override
    public IPage<E> setSize(long size) {
        this.size = size;
        return this;
    }

    @Override
    public IPage<E> setCurrent(long current) {
        this.current = current;
        return this;
    }

    public void setOrders(List<OrderItem> orders) {
        this.orders = orders;
    }

    public void setOptimizeCountSql(boolean optimizeCountSql) {
        this.optimizeCountSql = optimizeCountSql;
    }

    public void setSearchCount(boolean searchCount) {
        this.searchCount = searchCount;
    }

    public void setCountId(String countId) {
        this.countId = countId;
    }

    public void setMaxLimit(Long maxLimit) {
        this.maxLimit = maxLimit;
    }
}
