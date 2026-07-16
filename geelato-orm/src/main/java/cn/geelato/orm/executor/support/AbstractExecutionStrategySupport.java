package cn.geelato.orm.executor.support;

import cn.geelato.core.Fn;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.datasource.DynamicDataSourceHolder;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 提供执行策略无关的命令预处理、数据源解析和值表达式解析能力。
 */
public abstract class AbstractExecutionStrategySupport {

    protected static final String PRIMARY_CONNECT_ID = "primary";
    protected static final String VARS_PARENT = "$parent";
    protected static final String VARS_CTX = "$ctx";
    protected static final String VARS_FN = "$fn";

    protected final MetaManager metaManager = MetaManager.singleInstance();

    protected QueryCommand prepareQueryCommand(QueryCommand command) {
        processQueryCommandFunctions(command);
        return command;
    }

    protected void processQueryCommandFunctions(QueryCommand command) {
        if (command == null || command.getWhere() == null || command.getWhere().getParams() == null) {
            return;
        }
        Map<String, Object> params = command.getWhere().getParams();
        params.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String valueText = value.toString();
            if (!valueText.startsWith(VARS_FN)) {
                return;
            }
            String fnName = valueText.substring(VARS_FN.length() + 1);
            String resolvedValue = switch (fnName) {
                case "now", "nowDateTime" -> Fn.nowDateTime();
                case "nowDate" -> Fn.nowDate();
                default -> null;
            };
            if (command.getWhere().getFilters() != null) {
                command.getWhere().getFilters().stream()
                        .filter(filter -> value.equals(filter.getValue()))
                        .forEach(filter -> filter.setValue(resolvedValue));
            }
            params.put(key, resolvedValue);
        });
    }

    protected Object parseValueExp(SaveCommand currentCommand, Object valueExp, int times) {
        String valueExpTrim = valueExp.toString().trim();
        if (valueExpTrim.startsWith(VARS_CTX)) {
            return new SessionCtx().get(valueExpTrim.substring(VARS_CTX.length() + 1));
        }
        if (valueExpTrim.startsWith(VARS_FN)) {
            String fnName = valueExpTrim.substring(VARS_FN.length() + 1);
            return switch (fnName) {
                case "now", "nowDateTime" -> Fn.nowDateTime();
                case "nowDate" -> Fn.nowDate();
                default -> null;
            };
        }
        if (valueExpTrim.startsWith(VARS_PARENT)) {
            return parseValueExp((SaveCommand) currentCommand.getParentCommand(),
                    valueExpTrim.substring(VARS_PARENT.length() + 1), times + 1);
        }
        if (times == 0) {
            return valueExp;
        }
        if (currentCommand.getValueMap().containsKey(valueExpTrim)) {
            return currentCommand.getValueMap().get(valueExpTrim);
        }
        if ("id".equals(valueExpTrim)) {
            return currentCommand.getPK();
        }
        return null;
    }

    protected void prepareSaveValues(SaveCommand command) {
        command.getValueMap().forEach((key, value) -> {
            if (value != null && !(value instanceof FunctionFieldValue)) {
                command.getValueMap().put(key, parseValueExp(command, value, 0));
            }
        });
    }

    protected String resolveQueryConnectId(QueryCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    protected String resolveSaveConnectId(SaveCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    protected String resolveDeleteConnectId(DeleteCommand command) {
        return resolveEntityConnectId(command != null ? command.getEntityName() : null);
    }

    protected String resolveConnectId(String connectIdOverride, String entityConnectId) {
        if (StringUtils.hasText(connectIdOverride)) {
            return connectIdOverride;
        }
        if (StringUtils.hasText(entityConnectId)) {
            return entityConnectId;
        }
        String defaultDataSourceKey = DataSourceManager.singleInstance().getDefaultDataSourceKey();
        return StringUtils.hasText(defaultDataSourceKey) ? defaultDataSourceKey : PRIMARY_CONNECT_ID;
    }

    protected <T> T withDataSource(String connectId, Supplier<T> supplier) {
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
}
