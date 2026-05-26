package cn.geelato.orm.executor;

import cn.geelato.core.Fn;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.orm.PageResult;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认的元数据命令执行器，复用 SqlManager 与 Dao。
 */
public class DefaultMetaCommandExecutor implements MetaCommandExecutor {

    private static final String VARS_PARENT = "$parent";
    private static final String VARS_CTX = "$ctx";
    private static final String VARS_FN = "$fn";

    private final Dao dao;
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final MetaManager metaManager = MetaManager.singleInstance();

    public DefaultMetaCommandExecutor(Dao dao) {
        this.dao = dao;
    }

    @Override
    public Map<String, Object> queryForMap(QueryCommand command) {
        return queryForMap(command, null);
    }

    @Override
    public Map<String, Object> queryForMap(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            QueryCommand prepared = prepareQueryCommand(command);
            BoundSql boundSql = sqlManager.generateQuerySql(prepared);
            return dao.queryForMap(boundSql);
        });
    }

    @Override
    public <T> T queryForObject(QueryCommand command, Class<T> requiredType) {
        return queryForObject(command, requiredType, null);
    }

    @Override
    public <T> T queryForObject(QueryCommand command, Class<T> requiredType, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            QueryCommand prepared = prepareQueryCommand(command);
            BoundSql boundSql = sqlManager.generateQuerySql(prepared);
            return dao.queryForObject(boundSql, requiredType);
        });
    }

    @Override
    public List<Map<String, Object>> queryForMapList(QueryCommand command) {
        return queryForMapList(command, null);
    }

    @Override
    public List<Map<String, Object>> queryForMapList(QueryCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            QueryCommand prepared = prepareQueryCommand(command);
            return dao.queryForMapList(sqlManager.generatePageQuerySql(prepared));
        });
    }

    @Override
    public <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType) {
        return queryForOneColumnList(command, elementType, null);
    }

    @Override
    public <T> List<T> queryForOneColumnList(QueryCommand command, Class<T> elementType, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveQueryConnectId(command)), () -> {
            QueryCommand prepared = prepareQueryCommand(command);
            BoundSql boundSql = sqlManager.generateQuerySql(prepared);
            return dao.queryForOneColumnList(boundSql, elementType);
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
            List<Map<String, Object>> records = dao.queryForMapList(boundPageSql);
            Long total = dao.queryTotal(boundPageSql);
            PageResult<Map<String, Object>> result = new PageResult<>(prepared.getPageNum(), prepared.getPageSize(), total != null ? total : 0, true);
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
            QueryCommand prepared = prepareQueryCommand(command);
            BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(prepared);
            Long total = dao.queryTotal(boundPageSql);
            return total != null ? total : 0L;
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
        List<String> returnPks = new ArrayList<>();
        if (transaction) {
            return withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(commandList.get(0))), () -> {
                DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(dao.getJdbcTemplate().getDataSource());
                TransactionStatus transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
                try {
                    for (SaveCommand saveCommand : commandList) {
                        returnPks.add(recursiveBatchSave(saveCommand, dataSourceTransactionManager, transactionStatus));
                    }
                    TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
                    return returnPks;
                } catch (RuntimeException ex) {
                    if (!transactionStatus.isCompleted()) {
                        TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                    }
                    throw ex;
                }
            });
        }
        for (SaveCommand saveCommand : commandList) {
            returnPks.add(withDataSource(resolveConnectId(connectIdOverride, resolveSaveConnectId(saveCommand)), () -> {
                BoundSql boundSql = sqlManager.generateSaveSql(saveCommand);
                return dao.save(boundSql);
            }));
        }
        return returnPks;
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
            List<BoundSql> boundSqlList = sqlManager.generateBatchSaveSql(commandList);
            return dao.multiSave(boundSqlList);
        });
    }

    @Override
    public int delete(DeleteCommand command) {
        return delete(command, null);
    }

    @Override
    public int delete(DeleteCommand command, String connectIdOverride) {
        return withDataSource(resolveConnectId(connectIdOverride, resolveDeleteConnectId(command)), () -> {
            BoundSql boundSql = sqlManager.generateDeleteSql(command);
            return dao.delete(boundSql);
        });
    }

    private QueryCommand prepareQueryCommand(QueryCommand command) {
        processQueryCommandFunctions(command);
        return command;
    }

    private void processQueryCommandFunctions(QueryCommand command) {
        if (command == null || command.getWhere() == null || command.getWhere().getParams() == null) {
            return;
        }
        Map<String, Object> params = command.getWhere().getParams();
        params.forEach((key, value) -> {
            if (value != null) {
                String valStr = value.toString();
                if (valStr.startsWith(VARS_FN)) {
                    String fnName = valStr.substring(VARS_FN.length() + 1);
                    String newValue = switch (fnName) {
                        case "now", "nowDateTime" -> Fn.nowDateTime();
                        case "nowDate" -> Fn.nowDate();
                        default -> null;
                    };
                    if (command.getWhere().getFilters() != null) {
                        command.getWhere().getFilters().stream()
                                .filter(filter -> value.equals(filter.getValue()))
                                .forEach(filter -> filter.setValue(newValue));
                    }
                    params.put(key, newValue);
                }
            }
        });
    }

    private String recursiveSave(SaveCommand command) {
        command.getValueMap().forEach((key, value) -> {
            if (value != null && !(value instanceof FunctionFieldValue)) {
                command.getValueMap().put(key, parseValueExp(command, value, 0));
            }
        });
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        String pk = dao.save(boundSql);
        if (command.hasCommands()) {
            for (SaveCommand subCommand : command.getCommands()) {
                recursiveSave(subCommand);
            }
        }
        return pk;
    }

    private String recursiveBatchSave(SaveCommand command, DataSourceTransactionManager manager, TransactionStatus status) {
        return withDataSource(resolveSaveConnectId(command), () -> {
            BoundSql boundSql = sqlManager.generateSaveSql(command);
            String pk;
            try {
                pk = dao.save(boundSql);
            } catch (RuntimeException ex) {
                if (!status.isCompleted()) {
                    TransactionHelper.rollbackTransaction(manager, status);
                }
                throw ex;
            }
            if (command.hasCommands()) {
                for (SaveCommand subCommand : command.getCommands()) {
                    subCommand.getValueMap().forEach((key, value) -> {
                        if (value != null && !(value instanceof FunctionFieldValue)) {
                            subCommand.getValueMap().put(key, parseValueExp(subCommand, value, 0));
                        }
                    });
                    recursiveBatchSave(subCommand, manager, status);
                }
            }
            return pk;
        });
    }

    private Object parseValueExp(SaveCommand currentCommand, Object valueExp, int times) {
        String valueExpTrim = valueExp.toString().trim();
        if (valueExpTrim.startsWith(VARS_CTX)) {
            return new SessionCtx().get(valueExpTrim.substring(VARS_CTX.length() + 1));
        } else if (valueExpTrim.startsWith(VARS_FN)) {
            String fnName = valueExpTrim.substring(VARS_FN.length() + 1);
            return switch (fnName) {
                case "now", "nowDateTime" -> Fn.nowDateTime();
                case "nowDate" -> Fn.nowDate();
                default -> null;
            };
        } else if (valueExpTrim.startsWith(VARS_PARENT)) {
            return parseValueExp((SaveCommand) currentCommand.getParentCommand(), valueExpTrim.substring(VARS_PARENT.length() + 1), times + 1);
        } else if (times == 0) {
            return valueExp;
        } else if (currentCommand.getValueMap().containsKey(valueExpTrim)) {
            return currentCommand.getValueMap().get(valueExpTrim);
        } else if ("id".equals(valueExpTrim)) {
            return currentCommand.getPK();
        }
        return null;
    }

    private String resolveQueryConnectId(QueryCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    private String resolveSaveConnectId(SaveCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    private String resolveDeleteConnectId(DeleteCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    private String resolveEntityConnectId(String entityName) {
        if (!StringUtils.hasText(entityName) || !metaManager.containsEntity(entityName)) {
            return null;
        }
        EntityMeta entityMeta = metaManager.getByEntityName(entityName);
        if (entityMeta == null || entityMeta.getTableMeta() == null) {
            return null;
        }
        return entityMeta.getTableMeta().getConnectId();
    }

    private String resolveConnectId(String connectIdOverride, String entityConnectId) {
        return StringUtils.hasText(connectIdOverride) ? connectIdOverride : entityConnectId;
    }

    private <T> T withDataSource(String connectId, SupplierWithException<T> supplier) {
        String previous = DynamicDataSourceHolder.getDataSourceKey();
        try {
            if (StringUtils.hasText(connectId)) {
                DynamicDataSourceHolder.setDataSourceKey(connectId);
            }
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previous)) {
                DynamicDataSourceHolder.setDataSourceKey(previous);
            } else {
                DynamicDataSourceHolder.clearDataSourceKey();
            }
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
