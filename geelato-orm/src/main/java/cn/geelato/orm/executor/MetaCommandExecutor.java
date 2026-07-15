package cn.geelato.orm.executor;

import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.orm.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 统一执行 Fluent DSL / 元数据命令的门面。
 * 具体 SQL 落地由下游 ExecutionStrategy SPI 决定，当前可按配置切换 Dao 或 JdbcTemplate 实现。
 */
public interface MetaCommandExecutor {

    Map<String, Object> queryForMap(QueryCommand command);

    Map<String, Object> queryForMap(QueryCommand command, String connectIdOverride);

    Map<String, Object> callForMap(String callSql, Object[] params);

    Map<String, Object> callForMap(String callSql, Object[] params, String connectIdOverride);

    Map<String, Object> nativeQueryForMap(String sql, Object[] params);

    Map<String, Object> nativeQueryForMap(String sql, Object[] params, String connectIdOverride);

    <T> T queryForObject(QueryCommand command, Class<T> requiredType);

    <T> T queryForObject(QueryCommand command, Class<T> requiredType, String connectIdOverride);

    List<Map<String, Object>> queryForMapList(QueryCommand command);

    List<Map<String, Object>> queryForMapList(QueryCommand command, String connectIdOverride);

    List<Map<String, Object>> callForMapList(String callSql, Object[] params);

    List<Map<String, Object>> callForMapList(String callSql, Object[] params, String connectIdOverride);

    List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params);

    List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params, String connectIdOverride);

    <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType);

    <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType, String connectIdOverride);

    int nativeExecute(String sql, Object[] params);

    int nativeExecute(String sql, Object[] params, String connectIdOverride);

    <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType);

    <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType, String connectIdOverride);

    PageResult<Map<String, Object>> queryForPage(QueryCommand command);

    PageResult<Map<String, Object>> queryForPage(QueryCommand command, String connectIdOverride);

    long count(QueryCommand command);

    long count(QueryCommand command, String connectIdOverride);

    String save(SaveCommand command);

    String save(SaveCommand command, String connectIdOverride);

    List<String> batchSave(List<SaveCommand> commandList, boolean transaction);

    List<String> batchSave(List<SaveCommand> commandList, boolean transaction, String connectIdOverride);

    List<String> multiSave(List<SaveCommand> commandList);

    List<String> multiSave(List<SaveCommand> commandList, String connectIdOverride);

    int delete(DeleteCommand command);

    int delete(DeleteCommand command, String connectIdOverride);
}
