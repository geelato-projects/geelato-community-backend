package cn.geelato.orm.query;

import cn.geelato.orm.function.WrapperResultFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 原生 SQL 直通执行构建器。
 * 适用于业务侧已持有完整 SQL 语句，且希望继续复用 ORM 的动态数据源和统一执行入口。
 */
public class MetaNativeSql extends MetaOperate<MetaNativeSql> {
    private final String sql;
    private final List<Object> params = new ArrayList<>();
    private WrapperResultFunction<?, ?> wrapperFunction;

    public MetaNativeSql(String sql) {
        this.sql = sql;
    }

    public MetaNativeSql param(Object value) {
        this.params.add(value);
        return this;
    }

    public MetaNativeSql params(Object... values) {
        if (values != null && values.length > 0) {
            this.params.addAll(Arrays.asList(values));
        }
        return this;
    }

    public <T, R> MetaNativeSql wrapperResult(WrapperResultFunction<T, R> wrapperFunction) {
        this.wrapperFunction = wrapperFunction;
        return this;
    }

    public String toSql() {
        return sql;
    }

    public Object[] resolveParams() {
        return params.toArray();
    }

    @SuppressWarnings("unchecked")
    public <R> List<R> list() {
        List<Map<String, Object>> rows = executor().nativeQueryForMapList(sql, resolveParams(), getConnectId());
        if (wrapperFunction == null) {
            return (List<R>) rows;
        }
        return rows.stream()
                .map(row -> (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <R> R one() {
        Map<String, Object> row = executor().nativeQueryForMap(sql, resolveParams(), getConnectId());
        if (wrapperFunction == null) {
            return (R) row;
        }
        return (R) ((WrapperResultFunction<Map<String, Object>, ?>) wrapperFunction).apply(row);
    }

    public <T> T queryForObject(Class<T> requiredType) {
        return executor().nativeQueryForObject(sql, resolveParams(), requiredType, getConnectId());
    }

    public int execute() {
        return executor().nativeExecute(sql, resolveParams(), getConnectId());
    }
}
