package cn.geelato.orm.executor;

import cn.geelato.orm.WrapperResultFunction;
import cn.geelato.orm.query.MetaQuery;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
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
            String columnName = rs.getMetaData().getColumnName(i);
            Object value = rs.getObject(i);
            row.put(columnName, value);
        }
        return row;
    };
    
    @Override
    public List<Map<String, Object>> executeQuery(MetaQuery query) {
        String sql = query.toSql();
        System.out.println("执行查询SQL: " + sql);
        
        return jdbcTemplate.query(sql, mapRowMapper);
    }
    
    @Override
    public Map<String, Object> executeQueryForObject(MetaQuery query) {
        List<Map<String, Object>> results = executeQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
    
    @Override
    public long executeCount(MetaQuery query) {
        String countSql = query.toCountSql();
        System.out.println("执行统计SQL: " + countSql);
        
        Long count = jdbcTemplate.queryForObject(countSql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public PageResult<Map<String, Object>> executePageQuery(MetaQuery query) {
        List<Map<String, Object>> data = executeQuery(query);
        long total = executeCount(query);
        
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setData(data);
        pageResult.setTotal(total);
        pageResult.setPageNum(query.getPageNum() != null ? query.getPageNum() : 1);
        pageResult.setPageSize(query.getPageSize() != null ? query.getPageSize() : 10);
        
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
        
        List<R> wrappedData = new ArrayList<>();
        for (Map<String, Object> rawData : rawPageResult.getData()) {
            R wrappedResult = (R) ((WrapperResultFunction<Map<String, Object>, R>) wrapperFunction).apply(rawData);
            wrappedData.add(wrappedResult);
        }
        
        PageResult<R> wrappedPageResult = new PageResult<>();
        wrappedPageResult.setData(wrappedData);
        wrappedPageResult.setTotal(rawPageResult.getTotal());
        wrappedPageResult.setPageNum(rawPageResult.getPageNum());
        wrappedPageResult.setPageSize(rawPageResult.getPageSize());
        
        return wrappedPageResult;
    }
}