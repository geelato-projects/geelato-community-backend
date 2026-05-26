package cn.geelato.orm.query;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.orm.Filter;
import cn.geelato.orm.PageResult;
import cn.geelato.orm.WrapperResultFunction;
import cn.geelato.orm.Order;
import cn.geelato.orm.support.QueryCommandAdapter;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 元数据查询构建器
 * 提供流式API构建SQL查询
 */
@Getter
public class MetaQuery extends MetaOperate<MetaQuery> {
    private String[] selectFields;
    private final List<Order> orders = new java.util.ArrayList<>();
    private Integer pageNum;
    private Integer pageSize;
    private WrapperResultFunction<?, ?> wrapperFunction;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();

    public MetaQuery(String entityName) {
        this.entityName = entityName;
    }

    public MetaQuery(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 设置查询字段
     * @param fields 字段数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery select(String[] fields) {
        this.selectFields = fields;
        return this;
    }
    
    /**
     * 添加单个过滤条件
     * @param filter 过滤条件
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }
    
    /**
     * 添加单个排序规则
     * @param order 排序规则
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery order(Order order) {
        this.orders.add(order);
        return this;
    }
    
    /**
     * 添加多个排序规则
     * @param orders 排序规则数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery order(Order... orders) {
        this.orders.addAll(Arrays.asList(orders));
        return this;
    }

    /**
     * 设置分页参数
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页数量
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery page(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        return this;
    }
    
    /**
     * 设置结果包装函数
     * @param wrapperFunction 结果包装函数
     * @param <T> 输入类型
     * @param <R> 返回类型
     * @return MetaQuery对象，支持链式调用
     */
    public <T, R> MetaQuery wrapperResult(WrapperResultFunction<T, R> wrapperFunction) {
        this.wrapperFunction = wrapperFunction;
        return this;
    }

    /**
     * 构建SQL语句
     * @return 完整的SQL查询语句
     */
    public String toSql() {
        if (pageNum != null && pageNum > 0 && pageSize != null && pageSize > 0) {
            return sqlManager.generatePageQuerySql(QueryCommandAdapter.forList(this)).getBoundSql().getSql();
        }
        return sqlManager.generateQuerySql(QueryCommandAdapter.forList(this)).getSql();
    }

    /**
     * 获取查询结果总数的SQL
     * @return 统计总数的SQL语句
     */
    public String toCountSql() {
        return sqlManager.generatePageQuerySql(QueryCommandAdapter.forList(this)).getCountSql();
    }

    @SuppressWarnings("unchecked")
    public <R> List<R> list() {
        List<Map<String, Object>> rows = executor().queryForMapList(QueryCommandAdapter.forList(this), getConnectId());
        if (wrapperFunction == null) {
            return (List<R>) rows;
        }
        return rows.stream()
                .map(row -> (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <R> R one() {
        Map<String, Object> row = executor().queryForMap(QueryCommandAdapter.forObject(this), getConnectId());
        if (wrapperFunction == null) {
            return (R) row;
        }
        return (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row);
    }

    @SuppressWarnings("unchecked")
    public <R> PageResult<R> page() {
        PageResult<Map<String, Object>> raw = executor().queryForPage(QueryCommandAdapter.forList(this), getConnectId());
        if (wrapperFunction == null) {
            return (PageResult<R>) raw;
        }
        PageResult<R> wrapped = new PageResult<>(raw.getCurrent(), raw.getSize(), raw.getTotal(), raw.searchCount());
        wrapped.setRecords(raw.getRecords().stream()
                .map(row -> (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row))
                .collect(Collectors.toList()));
        return wrapped;
    }

    public long count() {
        return executor().count(QueryCommandAdapter.forList(this), getConnectId());
    }

    public boolean exists() {
        return count() > 0;
    }

    public <T> List<T> oneColumn(Class<T> elementType) {
        return executor().queryForOneColumnList(QueryCommandAdapter.forList(this), elementType, getConnectId());
    }

    public String[] resolveSelectFields() {
        if (selectFields != null && selectFields.length > 0) {
            return selectFields;
        }
        return metaManager.getByEntityName(resolveEntityName()).getFieldNames();
    }

    public String debug() {
        return Objects.toString(toSql(), "");
    }
}
