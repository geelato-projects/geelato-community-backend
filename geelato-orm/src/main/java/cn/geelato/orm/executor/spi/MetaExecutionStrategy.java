package cn.geelato.orm.executor.spi;

import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * MetaCommandExecutor 下游真正负责执行 SQL 的执行策略 SPI。
 */
public interface MetaExecutionStrategy {

    Map<String, Object> queryForMap(BoundSql boundSql);

    <T> T queryForObject(BoundSql boundSql, Class<T> requiredType);

    List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql);

    long queryTotal(BoundPageSql boundPageSql);

    <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType);

    Map<String, Object> callForMap(String callSql, Object[] params);

    List<Map<String, Object>> callForMapList(String callSql, Object[] params);

    Map<String, Object> nativeQueryForMap(String sql, Object[] params);

    List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params);

    <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType);

    int nativeExecute(String sql, Object[] params);

    String save(BoundSql boundSql);

    List<String> multiSave(List<BoundSql> boundSqlList);

    int delete(BoundSql boundSql);

    DataSource getDataSource();
}
