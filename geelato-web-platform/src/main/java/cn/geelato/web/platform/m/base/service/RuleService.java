package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.Fn;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.biz.rules.BizManagerFactory;
import cn.geelato.core.biz.rules.common.EntityValidateRule;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.gql.GqlManager;
import cn.geelato.core.gql.command.DeleteCommand;
import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.DaoException;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.script.rule.BizMvelRuleManager;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.datasource.annotion.UseDynamicDataSource;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.cache.CacheUtil;
import io.opentracing.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author geelato
 */
@Component
@Slf4j
public class RuleService {

    @Autowired
    @Qualifier("dynamicDao")
    public Dao dao;
    private final GqlManager gqlManager = GqlManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final BizMvelRuleManager bizMvelRuleManager = BizManagerFactory.getBizMvelRuleManager("mvelRule");
    private final RulesEngine rulesEngine = new DefaultRulesEngine();
    private final static String VARS_PARENT = "$parent";
    private final static String VARS_CTX = "$ctx";
    // $fn.now.
    private final static String VARS_FN = "$fn";


    public Map<String, Object> queryForMap(String gql) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        return dao.queryForMap(boundSql);
    }

    public <T> T queryForObject(String gql, Class<T> requiredType) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        return dao.queryForObject(boundSql, requiredType);
    }


    public ApiPagedResult<List<Map<String, Object>>> queryForMapList(String gql, boolean withMeta) {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
        List<Map<String, Object>> list = dao.queryForMapList(boundPageSql);
        Long total = dao.queryTotal(boundPageSql);
        ApiPagedResult<List<Map<String, Object>>> result = ApiPagedResult.success(list, command.getPageNum(), command.getPageSize(), list.size(), total);
        if (withMeta) {
            result.setMeta(metaManager.getByEntityName(command.getEntityName()).getSimpleFieldMetas(command.getFields()));
        }
        return result;
    }

    /**
     * @param entity 与platform_tree_node 关联的业务实体带有tree_node_id字段
     */
    public ApiResult<List<Map>> queryForTreeNodeList(String entity, Long treeId) {
        if (!metaManager.containsEntity(entity)) {
            return ApiResult.fail("不存在该实体");
        }
        Map params = new HashedMap();
        EntityMeta entityMeta = metaManager.getByEntityName(entity);
        params.put("tableName", entityMeta.getTableName());
        params.put("treeId", treeId);
        return ApiResult.success(dao.queryForMapList("select_tree_node_left_join", params));
    }

    /**
     * @param entity 与platform_tree_node 关联的业务实体带有tree_node_id字段
     */
    public ApiResult queryForTree(String entity, long treeId, String childrenKey) {
        ApiResult<List<Map>> result = queryForTreeNodeList(entity, treeId);
        if (!result.isSuccess()) {
            return result;
        }
        return ApiResult.success(toTree(result.getData(), treeId, childrenKey));
    }

    /**
     * 将列表数据转换为树形结构
     *
     * @param itemList    数据列表，包含每个元素的基本信息
     * @param parentId    父级ID，用于确定树形结构的根节点
     * @param childrenKey 子节点键名，例如 "items"、"children"，用于在树形结构中存储子节点
     * @return 转换后的树形结构列表
     */
    private List<Map> toTree(List<Map> itemList, long parentId, String childrenKey) {
        List<Map> resultList = new ArrayList<>();
        List<Map> toParseList = new ArrayList<>();
        for (Map item : itemList) {
            long parent = Long.parseLong(item.get("tn_parent").toString());
            if (parentId == parent) {
                resultList.add(item);
            } else {
                toParseList.add(item);
            }
        }

        if (!resultList.isEmpty()) {
            for (Map item : resultList) {
                List<Map> items = toTree(toParseList, Long.parseLong(item.get("tn_id").toString()), childrenKey);
                if (!items.isEmpty()) {
                    item.put(childrenKey, items);
                }
            }
        }

        return resultList;
    }

    public ApiMultiPagedResult queryForMultiMapList(String gql, boolean withMeta) {
        Map<String, ApiMultiPagedResult.PageData> dataMap = new HashMap<>();
        List<QueryCommand> commandList = gqlManager.generateMultiQuerySql(gql);
        for (QueryCommand command : commandList) {
            BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
            dataMap.put(command.getEntityName(), dao.queryForMapListToPageData(boundPageSql, withMeta));
        }
        ApiMultiPagedResult result = new ApiMultiPagedResult();
        result.setData(dataMap);
        return result;
    }

    public <T> List<T> queryForOneColumnList(String gql, Class<T> elementType) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        return dao.queryForOneColumnList(boundSql, elementType);
    }

//    @Transactional
    public String save(String biz, String gql) throws DaoException {
        SaveCommand command = gqlManager.generateSaveSql(gql, getSessionCtx());
        Facts facts = new Facts();
        facts.put("saveCommand", command);
        // TODO 通过biz获取业务规则，包括：内置的规则（实体检查），自定义规则（script脚本）
        Rules rules = new Rules();
        bizMvelRuleManager.getRule(biz);
        rules.register(new EntityValidateRule());
        rulesEngine.fire(rules, facts);
        return recursiveSave(command);
    }

    public Object batchSave(String gql, Boolean transaction) throws DaoException {
        List<String> returnPks = new ArrayList<>();
        List<SaveCommand> commandList = gqlManager.generateBatchSaveSql(gql, getSessionCtx());
        if (transaction) {
            DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(dao.getJdbcTemplate().getDataSource());
            TransactionStatus transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
            for (SaveCommand saveCommand : commandList) {
                String pkValue = recursiveBatchSave(saveCommand, dataSourceTransactionManager, transactionStatus);
                if ("saveFail".equals(pkValue)) {
                    TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                    break;
                } else {
                    returnPks.add(pkValue);
                }
            }
            TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        } else {
            for (SaveCommand saveCommand : commandList) {
                BoundSql boundSql = sqlManager.generateSaveSql(saveCommand);
                String pkValue = dao.save(boundSql);
                if ("saveFail".equals(pkValue)) {
                    continue;
                } else {
                    returnPks.add(pkValue);
                }
            }
        }
        return returnPks;
    }


    public Object multiSave(String gql) {
        List<SaveCommand> commandList = gqlManager.generateMultiSaveSql(gql, getSessionCtx());
        List<BoundSql> boundSqlList = sqlManager.generateBatchSaveSql(commandList);
        return dao.multiSave(boundSqlList);
    }

    /**
     * 递归执行，存在需解析依赖变更的情况
     * 不执行业务规则检查
     */
    public String recursiveSave(SaveCommand command, DataSourceTransactionManager dataSourceTransactionManager, TransactionStatus transactionStatus) throws DaoException {
        command.getValueMap().forEach((key, value) -> {
            if (value != null && !(value instanceof FunctionFieldValue)) {
                command.getValueMap().put(key, parseValueExp(command, value.toString(), 0));
            }
        });
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        String rtnValue;
        try {
            // todo : wait to refactor by aspect
            String connectId = metaManager.getByEntityName(command.getEntityName()).getTableMeta().getConnectId();
            if (!StringUtils.isEmpty(connectId)) {
                DynamicDataSourceHolder.setDataSourceKey(connectId);
                JdbcTemplate jdbcTemplate= new JdbcTemplate(DataSourceManager.singleInstance().getDataSource(connectId));
                Dao saveDao=new Dao(jdbcTemplate);
                rtnValue = saveDao.save(boundSql);
            }else{
                rtnValue=dao.save(boundSql);
            }
            String cacheKey = command.getEntityName() + "_" + rtnValue;
            if (CacheUtil.exists(cacheKey)) {
                CacheUtil.remove(cacheKey);
            }
        } catch (DaoException e) {
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            throw e;
        }
        command.setExecution(!"saveFail".equals(rtnValue));
        if (command.hasCommands()) {
            command.getCommands().forEach(subCommand -> {
                try {
                    recursiveSave(subCommand, dataSourceTransactionManager, transactionStatus);
                } catch (DaoException e) {
                    if (!transactionStatus.isCompleted()) {
                        TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                    }
                    throw e;
                }
            });
        }

        if (command.getParentCommand() == null && command.getExecution()) {
            TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
            log.info("transactionCommit");
        } else if (!command.getExecution() && !"transactionRollback".equals(rtnValue)) {
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            rtnValue = "transactionRollback";
            log.info("transactionRollback");
        }

        return rtnValue;
    }

    private String recursiveBatchSave(SaveCommand command, DataSourceTransactionManager dataSourceTransactionManager, TransactionStatus transactionStatus) {
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        String rtnValue;
        try {
            rtnValue = dao.save(boundSql);
        } catch (DaoException e) {
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            throw e;
        }
        command.setExecution(!"saveFail".equals(rtnValue));
        if (command.hasCommands()) {
            command.getCommands().forEach(subCommand -> {
                subCommand.getValueMap().forEach((key, value) -> {
                    if (value != null && !(value instanceof FunctionFieldValue)) {
                        subCommand.getValueMap().put(key, parseValueExp(subCommand, value.toString(), 0));
                    }
                });
                try {
                    recursiveBatchSave(subCommand, dataSourceTransactionManager, transactionStatus);
                } catch (DaoException e) {
                    if (!transactionStatus.isCompleted()) {
                        TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                    }
                    throw e;
                }
            });
        }
        return rtnValue;
    }

    public String recursiveSave(SaveCommand command) {
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        String rtnValue = dao.save(boundSql);
        //todo 缓存清除
        String cacheKey = command.getEntityName() + "_" + rtnValue;
        if (CacheUtil.exists(cacheKey)) {
            CacheUtil.remove(cacheKey);
        }
        if (command.hasCommands()) {
            command.getCommands().forEach(subCommand -> {
                subCommand.getValueMap().forEach((key, value) -> {
                    if (value != null && !(value instanceof FunctionFieldValue)) {
                        subCommand.getValueMap().put(key, parseValueExp(subCommand, value.toString(), 0));
                    }
                });
                recursiveSave(subCommand);
            });
        }
        return rtnValue;
    }

    /**
     * 解析值表达式
     * <p>
     * 解析给定的值表达式，并根据表达式的类型返回相应的值。
     *
     * @param currentCommand 当前保存命令对象，用于获取父命令和值映射
     * @param valueExp       要解析的值表达式，例如：$parent.name
     * @param times          递归调用的次数，初次调用时传入0，之后每次递归调用时自增
     * @return 根据表达式类型返回相应的值，如果无法解析则返回null
     */
    private Object parseValueExp(SaveCommand currentCommand, String valueExp, int times) {
        String valueExpTrim = valueExp.trim();
        if (valueExpTrim.startsWith(VARS_CTX)) {
            // 检查是否存在变更$ctx.userId等
            return getSessionCtx().get(valueExpTrim.substring(VARS_CTX.length() + 1));
        } else if (valueExpTrim.startsWith(VARS_FN)) {
            String fnName = valueExpTrim.substring(VARS_FN.length() + 1);
            // 检查是否存在变更$fn.now等
            return switch (fnName) {
                case "now", "nowDateTime" -> Fn.nowDateTime();
                case "nowDate" -> Fn.nowDate();
                default -> null;
            };
        } else if (valueExpTrim.startsWith(VARS_PARENT)) {
            // 检查是否存在变量$parent
            return parseValueExp((SaveCommand) currentCommand.getParentCommand(), valueExpTrim.substring(VARS_PARENT.length() + 1), times + 1);
        } else {
            if (times == 0) {
                // 如果是第一次且无VARS_PARENT关键字，则直接返回值
                return valueExp;
            } else {
                // 如果是updateCommand，id值不在valueMap中，可从PK值中获取
                // 如果是insertCommand，由于在执行子command时，父command已执行，此时已创建了新的id，存在valueMap中
                if (currentCommand.getValueMap().containsKey(valueExpTrim)) {
                    return currentCommand.getValueMap().get(valueExpTrim);
                } else if ("id".equals(valueExpTrim)) {
                    return currentCommand.getPK();
                }
                log.error("dao exception:通过表达式变量：{}获取不到值。", valueExp);
                // throw new DaoException("dao exception:通过表达式变量：" + valueExp + "获取不到值。");
                return null;
            }
        }
    }

    /**
     * 删除操作
     * <p>在删除之前，依据业务代码，从配置的业务规则库中读取规则，对command中的数据进行预处理，如更改相应的参数数据。</p>
     *
     * @param biz 业务代码
     * @return 主健值
     */
    public int delete(String biz, String id) {
        FilterGroup filterGroup;

        if (id.contains(",")) {
            filterGroup = new FilterGroup().addFilter("id", FilterGroup.Operator.in, id);
        } else {
            filterGroup = new FilterGroup().addFilter("id", id);
        }

        BoundSql boundSql = sqlManager.generateDeleteSql(biz, filterGroup);
        return dao.delete(boundSql);
    }

    public int deleteByGql(String biz, String gql) {
        DeleteCommand command = gqlManager.generateDeleteSql(gql, getSessionCtx());
        BoundSql boundSql = sqlManager.generateDeleteSql(command);
        return dao.delete(boundSql);
    }

    /**
     * @return 当前会话信息
     */
    protected SessionCtx getSessionCtx() {
        return new SessionCtx();
    }


}
