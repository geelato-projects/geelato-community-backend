package cn.geelato.orm.executor;

import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.MetaExecutionStrategy;
import cn.geelato.orm.executor.support.AbstractExecutionStrategySupport;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认的元数据命令执行器。
 * 对外维持统一 MetaCommandExecutor 门面，对下委托可插拔 ExecutionStrategy 执行 SQL。
 */
public class DefaultMetaCommandExecutor extends AbstractExecutionStrategySupport implements MetaCommandExecutor {

    private final MetaExecutionStrategy executionStrategy;
    private final SqlManager sqlManager = SqlManager.singleInstance();

    public DefaultMetaCommandExecutor(Dao dao) {
        this(new DaoMetaExecutionStrategy(dao));
    }

    public DefaultMetaCommandExecutor(MetaExecutionStrategy executionStrategy) {
        this.executionStrategy = executionStrategy;
    }

    public MetaExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

    @Override
    public Map<String, Object> queryForMap(QueryCommand command) {
        return queryForMap(command, null);
    }

    @Override
    public Map<String, Object> queryForMap(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            BoundSql boundSql = sqlManager.generateQuerySql(prepareQueryCommand(command));
            return executionStrategy.queryForMap(boundSql);
        });
    }

    @Override
    public Map<String, Object> callForMap(String callSql, Object[] params) {
        return callForMap(callSql, params, null);
    }

    @Override
    public Map<String, Object> callForMap(String callSql, Object[] params, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.callForMap(callSql, params));
    }

    @Override
    public Map<String, Object> nativeQueryForMap(String sql, Object[] params) {
        return nativeQueryForMap(sql, params, null);
    }

    @Override
    public Map<String, Object> nativeQueryForMap(String sql, Object[] params, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.nativeQueryForMap(sql, params));
    }

    @Override
    public <T> T queryForObject(QueryCommand command, Class<T> requiredType) {
        return queryForObject(command, requiredType, null);
    }

    @Override
    public <T> T queryForObject(QueryCommand command, Class<T> requiredType, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            BoundSql boundSql = sqlManager.generateQuerySql(prepareQueryCommand(command));
            return executionStrategy.queryForObject(boundSql, requiredType);
        });
    }

    @Override
    public List<Map<String, Object>> queryForMapList(QueryCommand command) {
        return queryForMapList(command, null);
    }

    @Override
    public List<Map<String, Object>> queryForMapList(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () ->
                executionStrategy.queryForMapList(sqlManager.generatePageQuerySql(prepareQueryCommand(command))));
    }

    @Override
    public List<Map<String, Object>> callForMapList(String callSql, Object[] params) {
        return callForMapList(callSql, params, null);
    }

    @Override
    public List<Map<String, Object>> callForMapList(String callSql, Object[] params, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.callForMapList(callSql, params));
    }

    @Override
    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params) {
        return nativeQueryForMapList(sql, params, null);
    }

    @Override
    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.nativeQueryForMapList(sql, params));
    }

    @Override
    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType) {
        return nativeQueryForObject(sql, params, requiredType, null);
    }

    @Override
    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.nativeQueryForObject(sql, params, requiredType));
    }

    @Override
    public int nativeExecute(String sql, Object[] params) {
        return nativeExecute(sql, params, null);
    }

    @Override
    public int nativeExecute(String sql, Object[] params, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, null), () -> executionStrategy.nativeExecute(sql, params));
    }

    @Override
    public <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType) {
        return queryForOneColumnList(command, elementType, null);
    }

    @Override
    public <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            BoundSql boundSql = sqlManager.generateQuerySql(prepareQueryCommand(command));
            return executionStrategy.queryForOneColumnList(boundSql, elementType);
        });
    }

    @Override
    public PageResult<Map<String, Object>> queryForPage(QueryCommand command) {
        return queryForPage(command, null);
    }

    @Override
    public PageResult<Map<String, Object>> queryForPage(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            QueryCommand prepared = prepareQueryCommand(command);
            BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(prepared);
            List<Map<String, Object>> records = executionStrategy.queryForMapList(boundPageSql);
            long total = executionStrategy.queryTotal(boundPageSql);
            PageResult<Map<String, Object>> result = new PageResult<>(prepared.getPageNum(), prepared.getPageSize(), total, true);
            result.setRecords(records);
            return result;
        });
    }

    @Override
    public long count(QueryCommand command) {
        return count(command, null);
    }

    @Override
    public long count(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(prepareQueryCommand(command));
            return executionStrategy.queryTotal(boundPageSql);
        });
    }

    @Override
    public String save(SaveCommand command) {
        return save(command, null);
    }

    @Override
    public String save(SaveCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(command)), () -> recursiveSave(command));
    }

    @Override
    public List<String> batchSave(List<SaveCommand> commandList, boolean transaction) {
        return batchSave(commandList, transaction, null);
    }

    @Override
    public List<String> batchSave(List<SaveCommand> commandList, boolean transaction, String connectIdOverride) {
        if (commandList == null || commandList.isEmpty()) {
            return new ArrayList<>();
        }
        if (!transaction) {
            List<String> result = new ArrayList<>();
            for (SaveCommand command : commandList) {
                result.add(withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(command)), () -> {
                    prepareSaveValues(command);
                    return executionStrategy.save(sqlManager.generateSaveSql(command));
                }));
            }
            return result;
        }
        return withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(commandList.get(0))), () -> {
            DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(requiredDataSource());
            TransactionStatus status = TransactionHelper.beginTransaction(transactionManager);
            List<String> returnPks = new ArrayList<>();
            try {
                for (SaveCommand command : commandList) {
                    returnPks.add(recursiveBatchSave(command, transactionManager, status));
                }
                TransactionHelper.commitTransaction(transactionManager, status);
                return returnPks;
            } catch (RuntimeException ex) {
                if (!status.isCompleted()) {
                    TransactionHelper.rollbackTransaction(transactionManager, status);
                }
                throw ex;
            }
        });
    }

    @Override
    public List<String> multiSave(List<SaveCommand> commandList) {
        return multiSave(commandList, null);
    }

    @Override
    public List<String> multiSave(List<SaveCommand> commandList, String connectIdOverride) {
        if (commandList == null || commandList.isEmpty()) {
            return new ArrayList<>();
        }
        return withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(commandList.get(0))), () -> {
            commandList.forEach(this::prepareSaveValues);
            List<BoundSql> boundSqlList = sqlManager.generateBatchSaveSql(commandList);
            return executionStrategy.multiSave(boundSqlList);
        });
    }

    @Override
    public int delete(DeleteCommand command) {
        return delete(command, null);
    }

    @Override
    public int delete(DeleteCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveDeleteConnectId(command)), () ->
                executionStrategy.delete(sqlManager.generateDeleteSql(command)));
    }

    private String recursiveSave(SaveCommand command) {
        prepareSaveValues(command);
        String pk = executionStrategy.save(sqlManager.generateSaveSql(command));
        if (command.hasCommands()) {
            for (SaveCommand subCommand : command.getCommands()) {
                recursiveSave(subCommand);
            }
        }
        return pk;
    }

    private String recursiveBatchSave(SaveCommand command, DataSourceTransactionManager manager, TransactionStatus status) {
        return withDataSource(resolveSaveConnectId(command), () -> {
            prepareSaveValues(command);
            String pk;
            try {
                pk = executionStrategy.save(sqlManager.generateSaveSql(command));
            } catch (RuntimeException ex) {
                if (!status.isCompleted()) {
                    TransactionHelper.rollbackTransaction(manager, status);
                }
                throw ex;
            }
            if (command.hasCommands()) {
                for (SaveCommand subCommand : command.getCommands()) {
                    recursiveBatchSave(subCommand, manager, status);
                }
            }
            return pk;
        });
    }

    private DataSource requiredDataSource() {
        DataSource dataSource = executionStrategy.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("MetaExecutionStrategy did not provide a DataSource for transactional save");
        }
        return dataSource;
    }
}
