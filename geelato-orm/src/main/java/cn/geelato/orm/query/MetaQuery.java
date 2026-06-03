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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 元数据查询构建器
 * 提供流式API构建SQL查询
 */
@Getter
public class MetaQuery extends MetaOperate<MetaQuery> {
    private String[] selectFields;
    private final List<Order> orders = new java.util.ArrayList<>();
    private final List<String> refFields = new ArrayList<>();
    private final Map<String, String> selectAliases = new LinkedHashMap<>();
    private final List<SelectExpr> selectExprs = new ArrayList<>();
    private final List<JoinClause> joins = new ArrayList<>();
    private String[] groupByFields;
    private String havingSql;
    private String tableAlias;
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

    public MetaQuery selectRef(String foreignField) {
        this.refFields.add(foreignField);
        return this;
    }

    public MetaQuery selectRef(String foreignField, String alias) {
        this.refFields.add(foreignField);
        if (alias != null && !alias.isBlank()) {
            this.selectAliases.put(foreignField, alias);
        }
        return this;
    }

    public MetaQuery selectExpr(String expression, String alias) {
        this.selectExprs.add(new SelectExpr(expression, alias));
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

    public MetaQuery as(String alias) {
        this.tableAlias = alias;
        return this;
    }

    public MetaQuery groupBy(String... fields) {
        this.groupByFields = fields;
        return this;
    }

    public MetaQuery havingSql(String sql) {
        this.havingSql = sql;
        return this;
    }

    public MetaQuery join(String entityName, String alias, JoinType type, Consumer<JoinOn> consumer) {
        JoinOn joinOn = new JoinOn();
        if (consumer != null) {
            consumer.accept(joinOn);
        }
        JoinClause joinClause = new JoinClause();
        joinClause.setEntityName(entityName);
        joinClause.setAlias(alias);
        joinClause.setJoinType(type);
        joinClause.setConditions(new ArrayList<>(joinOn.getConditions()));
        this.joins.add(joinClause);
        return this;
    }

    public MetaQuery join(Class<?> entityClass, String alias, JoinType type, Consumer<JoinOn> consumer) {
        return join(metaManager.get(entityClass).getEntityName(), alias, type, consumer);
    }

    public MetaQuery leftJoin(String entityName, String alias, Consumer<JoinOn> consumer) {
        return join(entityName, alias, JoinType.LEFT, consumer);
    }

    public MetaQuery leftJoin(Class<?> entityClass, String alias, Consumer<JoinOn> consumer) {
        return join(entityClass, alias, JoinType.LEFT, consumer);
    }

    public MetaQuery innerJoin(String entityName, String alias, Consumer<JoinOn> consumer) {
        return join(entityName, alias, JoinType.INNER, consumer);
    }

    public MetaQuery innerJoin(Class<?> entityClass, String alias, Consumer<JoinOn> consumer) {
        return join(entityClass, alias, JoinType.INNER, consumer);
    }

    public MetaQuery rightJoin(String entityName, String alias, Consumer<JoinOn> consumer) {
        return join(entityName, alias, JoinType.RIGHT, consumer);
    }

    public MetaQuery rightJoin(Class<?> entityClass, String alias, Consumer<JoinOn> consumer) {
        return join(entityClass, alias, JoinType.RIGHT, consumer);
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
        List<String> resolved = new ArrayList<>();
        if (selectFields != null && selectFields.length > 0) {
            resolved.addAll(Arrays.asList(selectFields));
        } else {
            resolved.addAll(Arrays.asList(metaManager.getByEntityName(resolveEntityName()).getFieldNames()));
        }
        resolved.addAll(refFields);
        return resolved.toArray(new String[0]);
    }

    public String debug() {
        return Objects.toString(toSql(), "");
    }
}
