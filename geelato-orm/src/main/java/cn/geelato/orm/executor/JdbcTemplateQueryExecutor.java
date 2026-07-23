package cn.geelato.orm.executor;

import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.function.WrapperResultFunction;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.adapter.QueryCommandAdapter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JdbcTemplate查询执行器实现
 * 集成Spring JdbcTemplate执行SQL查询
 */
public class JdbcTemplateQueryExecutor implements QueryExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final SqlManager sqlManager = SqlManager.singleInstance();
    
    public JdbcTemplateQueryExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 通用的RowMapper，将ResultSet转换为Map
     */
    private final RowMapper<Map<String, Object>> mapRowMapper = (rs, rowNum) -> {
        Map<String, Object> row = new HashMap<>();
        int columnCount = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = rs.getMetaData().getColumnLabel(i);
            Object value = rs.getObject(i);
            row.put(columnName, value);
        }
        return row;
    };
    
    @Override
    public List<Map<String, Object>> executeQuery(MetaQuery query) {
        BoundSql boundSql = resolveQueryBoundSql(query);
        System.out.println("执行查询SQL: " + boundSql);

        return query(boundSql, mapRowMapper);
    }
    
    @Override
    public Map<String, Object> executeQueryForObject(MetaQuery query) {
        List<Map<String, Object>> results = executeQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public long executeCount(MetaQuery query) {
        BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(QueryCommandAdapter.forList(query));
        BoundSql boundSql = boundPageSql.getBoundSql();
        String countSql = boundPageSql.getCountSql();
        System.out.println("执行统计SQL: " + countSql);

        Long count = boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.queryForObject(countSql, boundSql.getParams(), boundSql.getTypes(), Long.class)
                : jdbcTemplate.queryForObject(countSql, Long.class, boundSql.getParams());
        return count != null ? count : 0L;
    }
    
    @Override
    public PageResult<Map<String, Object>> executePageQuery(MetaQuery query) {
        List<Map<String, Object>> records = executeQuery(query);
        long total = executeCount(query);

        long current = query.getPageNum() != null ? query.getPageNum() : 1;
        long size = query.getPageSize() != null ? query.getPageSize() : 10;
        PageResult<Map<String, Object>> pageResult = new PageResult<>(current, size, total, true);
        pageResult.setRecords(records);

        return pageResult;
    }
    
    @Override
    public List<Map<String, Object>> executeSql(String sql) {
        System.out.println("执行原生SQL: " + sql);
        
        return jdbcTemplate.query(sql, mapRowMapper);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> executeQueryWithWrapper(MetaQuery query) {
        List<Map<String, Object>> rawResults = executeQuery(query);
        WrapperResultFunction<?, ?> wrapperFunction = query.getWrapperFunction();
        
        if (wrapperFunction == null) {
            throw new IllegalArgumentException("WrapperFunction不能为空");
        }
        
        List<R> wrappedResults = new ArrayList<>();
        for (Map<String, Object> rawResult : rawResults) {
            R wrappedResult = (R) ((WrapperResultFunction<Map<String, Object>, R>) wrapperFunction).apply(rawResult);
            wrappedResults.add(wrappedResult);
        }
        
        return wrappedResults;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> R executeQueryForObjectWithWrapper(MetaQuery query) {
        Map<String, Object> rawResult = executeQueryForObject(query);
        WrapperResultFunction<?, ?> wrapperFunction = query.getWrapperFunction();
        
        if (wrapperFunction == null) {
            throw new IllegalArgumentException("WrapperFunction不能为空");
        }
        
        if (rawResult == null) {
            return null;
        }
        
        return (R) ((WrapperResultFunction<Map<String, Object>, R>) wrapperFunction).apply(rawResult);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> PageResult<R> executePageQueryWithWrapper(MetaQuery query) {
        PageResult<Map<String, Object>> rawPageResult = executePageQuery(query);
        WrapperResultFunction<?, ?> wrapperFunction = query.getWrapperFunction();
        
        if (wrapperFunction == null) {
            throw new IllegalArgumentException("WrapperFunction不能为空");
        }
        
        List<R> wrappedRecords = new ArrayList<>();
        for (Map<String, Object> rawRecord : rawPageResult.getRecords()) {
            R wrappedResult = (R) ((WrapperResultFunction<Map<String, Object>, R>) wrapperFunction).apply(rawRecord);
            wrappedRecords.add(wrappedResult);
        }

        PageResult<R> wrappedPageResult = new PageResult<>(
                rawPageResult.getCurrent(),
                rawPageResult.getSize(),
                rawPageResult.getTotal(),
                rawPageResult.searchCount()
        );
        wrappedPageResult.setRecords(wrappedRecords);
        wrappedPageResult.setOrders(rawPageResult.orders());
        wrappedPageResult.setOptimizeCountSql(rawPageResult.optimizeCountSql());
        wrappedPageResult.setCountId(rawPageResult.countId());
        wrappedPageResult.setMaxLimit(rawPageResult.maxLimit());

        return wrappedPageResult;
    }

    private BoundSql resolveQueryBoundSql(MetaQuery query) {
        if (query.getPageNum() != null && query.getPageNum() > 0
                && query.getPageSize() != null && query.getPageSize() > 0) {
            return sqlManager.generatePageQuerySql(QueryCommandAdapter.forList(query)).getBoundSql();
        }
        return sqlManager.generateQuerySql(QueryCommandAdapter.forList(query));
    }

    private <T> List<T> query(BoundSql boundSql, RowMapper<T> rowMapper) {
        return boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), rowMapper)
                : jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), rowMapper);
    }
}
