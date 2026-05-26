package cn.geelato.orm.executor;

import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.orm.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 统一执行基于元数据命令的查询与写入能力。
 */
public interface MetaCommandExecutor {

    Map<String, Object> queryForMap(QueryCommand command);

    Map<String, Object> queryForMap(QueryCommand command, String connectIdOverride);

    <T> T queryForObject(QueryCommand command, Class<T> requiredType);

    <T> T queryForObject(QueryCommand command, Class<T> requiredType, String connectIdOverride);

    List<Map<String, Object>> queryForMapList(QueryCommand command);

    List<Map<String, Object>> queryForMapList(QueryCommand command, String connectIdOverride);

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
