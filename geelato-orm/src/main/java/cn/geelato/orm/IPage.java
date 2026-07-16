package cn.geelato.orm;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * 分页 Page 对象接口。
 * 从 MyBatis-Plus IPage 迁移而来，保留当前 ORM 所需的兼容签名。
 *
 * @param <T> 数据类型
 */
public interface IPage<T> extends Serializable {

    /**
     * 获取排序信息，排序的字段和正反序。
     *
     * @return 排序信息
     */
    List<OrderItem> orders();

    /**
     * 自动优化 COUNT SQL。
     *
     * @return true 是 / false 否
     */
    default boolean optimizeCountSql() {
        return true;
    }

    /**
     * 优化 join count sql。
     *
     * @return true 是 / false 否
     */
    default boolean optimizeJoinOfCountSql() {
        return true;
    }

    /**
     * 是否进行 count 查询。
     *
     * @return true 是 / false 否
     */
    default boolean searchCount() {
        return true;
    }

    /**
     * 计算当前分页偏移量。
     *
     * @return 偏移量
     */
    default long offset() {
        long current = getCurrent();
        if (current <= 1L) {
            return 0L;
        }
        return Math.max((current - 1) * getSize(), 0L);
    }

    /**
     * 最大每页分页数限制。
     *
     * @return 限制值
     */
    default Long maxLimit() {
        return null;
    }

    /**
     * 当前分页总页数。
     *
     * @return 总页数
     */
    default long getPages() {
        if (getSize() == 0) {
            return 0L;
        }
        long pages = getTotal() / getSize();
        if (getTotal() % getSize() != 0) {
            pages++;
        }
        return pages;
    }

    /**
     * 保留该方法以兼容反序列化入口。
     *
     * @param pages 页数
     * @return 当前分页对象
     */
    @Deprecated
    default IPage<T> setPages(long pages) {
        return this;
    }

    /**
     * 分页记录列表。
     *
     * @return 记录列表
     */
    List<T> getRecords();

    /**
     * 设置分页记录列表。
     *
     * @param records 记录列表
     * @return 当前分页对象
     */
    IPage<T> setRecords(List<T> records);

    /**
     * 当前满足条件总行数。
     *
     * @return 总条数
     */
    long getTotal();

    /**
     * 设置当前满足条件总行数。
     *
     * @param total 总条数
     * @return 当前分页对象
     */
    IPage<T> setTotal(long total);

    /**
     * 获取每页显示条数。
     *
     * @return 每页显示条数
     */
    long getSize();

    /**
     * 设置每页显示条数。
     *
     * @param size 每页显示条数
     * @return 当前分页对象
     */
    IPage<T> setSize(long size);

    /**
     * 当前页。
     *
     * @return 当前页
     */
    long getCurrent();

    /**
     * 设置当前页。
     *
     * @param current 当前页
     * @return 当前分页对象
     */
    IPage<T> setCurrent(long current);

    /**
     * IPage 的泛型转换。
     *
     * @param mapper 转换函数
     * @param <R> 转换后的泛型
     * @return 转换泛型后的 IPage
     */
    @SuppressWarnings("unchecked")
    default <R> IPage<R> convert(Function<? super T, ? extends R> mapper) {
        return ((IPage<R>) this).setRecords(this.getRecords().stream().map(mapper).collect(toList()));
    }

    /**
     * 计数 SQL 的 mapped statement id。
     *
     * @return id
     */
    default String countId() {
        return null;
    }
}
