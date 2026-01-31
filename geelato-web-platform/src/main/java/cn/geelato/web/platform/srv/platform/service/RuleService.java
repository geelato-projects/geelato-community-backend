package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.Fn;
import cn.geelato.core.GlobalContext;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.biz.rules.BizManagerFactory;
import cn.geelato.core.biz.rules.common.EntityValidateRule;
import cn.geelato.core.mql.MetaQLManager;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.script.rule.BizMvelRuleManager;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.utils.CacheUtil;
import cn.geelato.web.platform.cache.MetaCacheProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;

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
    private final MetaQLManager gqlManager = MetaQLManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final BizMvelRuleManager bizMvelRuleManager = BizManagerFactory.getBizMvelRuleManager("mvelRule");
    private final RulesEngine rulesEngine = new DefaultRulesEngine();
    private final static String VARS_PARENT = "$parent";
    private final static String VARS_CTX = "$ctx";
    // $fn.now.
    private final static String VARS_FN = "$fn";
    private final MetaCacheProvider<Object> metaCache = new MetaCacheProvider<>();


    public Map<String, Object> queryForMap(String gql) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        processQueryCommandFunctions(command);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        if (!GlobalContext.getMetaQueryCacheOption()) {
            return dao.queryForMap(boundSql);
        }
        String key = "query:" + command.getEntityName() + ":" + command.getCacheKey() + ":map";
        if (metaCache.exists(key)) {
            Object cached = metaCache.getCache(key);
            if (cached instanceof Map) {
                return (Map<String, Object>) cached;
            }
        }
        Map<String, Object> result = dao.queryForMap(boundSql);
        metaCache.putCache(key, result);
        return result;
    }

    public <T> T queryForObject(String gql, Class<T> requiredType) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        processQueryCommandFunctions(command);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        if (!GlobalContext.getMetaQueryCacheOption()) {
            return dao.queryForObject(boundSql, requiredType);
        }
        String key = "query:" + command.getEntityName() + ":" + command.getCacheKey() + ":obj:" + requiredType.getSimpleName();
        if (metaCache.exists(key)) {
            Object cached = metaCache.getCache(key);
            try{
                return (T) cached;
            }catch (ClassCastException e){
                // fall through to query
            }
        }
        T result = dao.queryForObject(boundSql, requiredType);
        if (requiredType == String.class || Number.class.isAssignableFrom(requiredType) || requiredType == Boolean.class) {
            metaCache.putCache(key, result);
        }
        return result;
    }


    public ApiPagedResult<List<Map<String, Object>>> queryForMapList(String gql, boolean withMeta) {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        processQueryCommandFunctions(command);
        BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
        if (!GlobalContext.getMetaQueryCacheOption()) {
            List<Map<String, Object>> list = dao.queryForMapList(boundPageSql);
            Long total = dao.queryTotal(boundPageSql);
            ApiPagedResult<List<Map<String, Object>>> result = ApiPagedResult.success(list, command.getPageNum(), command.getPageSize(), list != null ? list.size() : 0, total != null ? total : 0);
            if (withMeta) {
                result.setMeta(metaManager.getByEntityName(command.getEntityName()).getSimpleFieldMetas(command.getFields()));
            }
            return result;
        }
        String prefix = "query:" + command.getEntityName() + ":" + command.getCacheKey();
        String kList = prefix + ":list";
        String kTotal = prefix + ":total";
        ApiPagedResult<List<Map<String, Object>>> result;
        if (metaCache.exists(kList) && metaCache.exists(kTotal)) {
            List<Map<String, Object>> cachedList = (List<Map<String, Object>>) metaCache.getCache(kList);
            Long cachedTotal = (Long) metaCache.getCache(kTotal);
            result = ApiPagedResult.success(cachedList, command.getPageNum(), command.getPageSize(), cachedList != null ? cachedList.size() : 0, cachedTotal != null ? cachedTotal : 0);
            result.setCache(true);
        } else {
            List<Map<String, Object>> list = dao.queryForMapList(boundPageSql);
            Long total = dao.queryTotal(boundPageSql);
            metaCache.putCache(kList, list);
            metaCache.putCache(kTotal, total);
            result = ApiPagedResult.success(list, command.getPageNum(), command.getPageSize(), list != null ? list.size() : 0, total != null ? total : 0);
        }
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
        boolean allCached = GlobalContext.getMetaQueryCacheOption();
        for (QueryCommand command : commandList) {
            BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
            String prefix = "query:" + command.getEntityName() + ":" + command.getCacheKey();
            String kList = prefix + ":list";
            String kTotal = prefix + ":total";
            List<Map<String, Object>> list;
            Long total;
            if (GlobalContext.getMetaQueryCacheOption() && metaCache.exists(kList) && metaCache.exists(kTotal)) {
                list = (List<Map<String, Object>>) metaCache.getCache(kList);
                total = (Long) metaCache.getCache(kTotal);
            } else {
                allCached = false;
                list = dao.queryForMapList(boundPageSql);
                total = dao.queryTotal(boundPageSql);
                if (GlobalContext.getMetaQueryCacheOption()) {
                    metaCache.putCache(kList, list);
                    metaCache.putCache(kTotal, total);
                }
            }
            ApiMultiPagedResult.PageData apiPd = new ApiMultiPagedResult.PageData();
            apiPd.setData(list);
            apiPd.setTotal(total != null ? total : 0);
            apiPd.setPage(command.getPageNum());
            apiPd.setSize(command.getPageSize());
            apiPd.setDataSize(list != null ? list.size() : 0);
            if (withMeta) {
                apiPd.setMeta(metaManager.getByEntityName(command.getEntityName()).getSimpleFieldMetas(command.getFields()));
            }
            dataMap.put(command.getEntityName(), apiPd);
        }
        ApiMultiPagedResult result = new ApiMultiPagedResult();
        result.setData(dataMap);
        if (allCached) {
            result.setCache(true);
        }
        return result;
    }

    // 值适配已在 MetaCacheProvider 统一处理

    public <T> List<T> queryForOneColumnList(String gql, Class<T> elementType) throws DataAccessException {
        QueryCommand command = gqlManager.generateQuerySql(gql);
        processQueryCommandFunctions(command);
        BoundSql boundSql = sqlManager.generateQuerySql(command);
        if (!GlobalContext.getMetaQueryCacheOption()) {
            return dao.queryForOneColumnList(boundSql, elementType);
        }
        String key = "query:" + command.getEntityName() + ":" + command.getCacheKey() + ":col:" + elementType.getSimpleName();
        if (metaCache.exists(key)) {
            Object cached = metaCache.getCache(key);
            try{
                return (List<T>) cached;
            }catch (ClassCastException e){
                // fall through to query
            }
        }
        List<T> result = dao.queryForOneColumnList(boundSql, elementType);
        metaCache.putCache(key, result);
        return result;
    }

    /**
     * 统一处理 QueryCommand 中 where 参数的函数变量，如 $fn.now / $fn.nowDate / $fn.nowDateTime。
     */
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
                    String newValue;
                    switch (fnName) {
                        case "now", "nowDateTime" -> newValue = Fn.nowDateTime();
                        case "nowDate" -> newValue = Fn.nowDate();
                        default -> newValue = null;
                    }
                    if (command.getWhere().getFilters() != null) {
                        command.getWhere().getFilters().stream()
                                .filter(filter -> value.equals(filter.getValue()))
                                .forEach(filter -> filter.setValue(newValue));
                    }
                    params.replace(key, newValue);
                }
            }
        });
    }

//    @Transactional(
//            transactionManager = "dynamicDataSourceTransactionManager",
//            propagation = Propagation.REQUIRED,
//            isolation = Isolation.READ_COMMITTED,
//            rollbackFor = Exception.class
//    )
    public String save(String biz, String gql) {
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

    public Object batchSave(String gql, Boolean transaction) {
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
    public String recursiveSave(SaveCommand command, DataSourceTransactionManager dataSourceTransactionManager, TransactionStatus transactionStatus) {
        command.getValueMap().forEach((key, value) -> {
            if (value != null && !(value instanceof FunctionFieldValue)) {
                command.getValueMap().put(key, parseValueExp(command, value, 0));
            }
        });
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        String rtnValue;
        try {
            // todo : wait to refactor by aspect
            String connectId = metaManager.getByEntityName(command.getEntityName()).getTableMeta().getConnectId();
            if (!StringUtils.isEmpty(connectId)) {
                DynamicDataSourceHolder.setDataSourceKey(connectId);
            }
            rtnValue = dao.save(boundSql);
            String cacheKey = command.getEntityName() + "_" + rtnValue;
            if (CacheUtil.exists(cacheKey)) {
                CacheUtil.remove(cacheKey);
            }
        } catch (Exception e) {
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            throw e;
        }
        command.setExecution(!"saveFail".equals(rtnValue));
        if (command.hasCommands()) {
            command.getCommands().forEach(subCommand -> {
                try {
                    recursiveSave(subCommand, dataSourceTransactionManager, transactionStatus);
                } catch (Exception e) {
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
            String connectId = metaManager.getByEntityName(command.getEntityName()).getTableMeta().getConnectId();
            if (!StringUtils.isEmpty(connectId)) {
                DynamicDataSourceHolder.setDataSourceKey(connectId);
            }
            rtnValue = dao.save(boundSql);
        } catch (Exception e) {
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            throw e;
        }
        command.setExecution(!"saveFail".equals(rtnValue));
        if (command.hasCommands()) {
            command.getCommands().forEach(subCommand -> {
                subCommand.getValueMap().forEach((key, value) -> {
                    if (value != null && !(value instanceof FunctionFieldValue)) {
                        subCommand.getValueMap().put(key, parseValueExp(subCommand, value, 0));
                    }
                });
                try {
                    recursiveBatchSave(subCommand, dataSourceTransactionManager, transactionStatus);
                } catch (Exception e) {
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
                    if (value != null && !(value instanceof FunctionFieldValue) ) {
                        subCommand.getValueMap().put(key, parseValueExp(subCommand, value, 0));
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
    private Object parseValueExp(SaveCommand currentCommand, Object valueExp, int times) {
        String valueExpTrim = valueExp.toString().trim();
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
                log.error("parseValueExp:通过表达式变量：{}获取不到值。", valueExp);
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
