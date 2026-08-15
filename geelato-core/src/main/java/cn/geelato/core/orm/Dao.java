package cn.geelato.core.orm;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.QueryEventContext;
import cn.geelato.core.orm.event.QueryEventManager;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QueryViewCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.meta.Ignore;
import cn.geelato.lang.meta.IgnoreType;
import cn.geelato.core.meta.model.CommonRowMapper;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.lang.api.DataItems;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * @author geemeta
 */
@Slf4j
@SuppressWarnings({"rawtypes", "SqlSourceToSinkFlow"})
public class Dao extends SqlKeyDao {
    private Boolean defaultFilterOption = false;
    private FilterGroup defaultFilterGroup;
    public Dao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setDefaultFilter(Boolean defaultFilter, FilterGroup defaultFilterGroup) {
        this.defaultFilterOption = defaultFilter;
        this.defaultFilterGroup = defaultFilterGroup;
    }

    //========================================================
    //                  内部 helper 方法                    ==
    //========================================================

    /**
     * 统一异常转换模板：将 DataAccessException 转为 SqlExecuteException
     */
    private <T> T execute(BoundSql bs, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            throw new SqlExecuteException(e, bs.getSql(), bs.getParams());
        }
    }

    private void executeVoid(BoundSql bs, Runnable action) {
        try {
            action.run();
        } catch (DataAccessException e) {
            throw new SqlExecuteException(e, bs.getSql(), bs.getParams());
        }
    }

    /**
     * 带 types 判断的 update，消除全类重复的 if-else 分支
     */
    private int updateWithTypes(BoundSql bs) {
        return bs.getTypes() != null && bs.getTypes().length > 0
                ? jdbcTemplate.update(bs.getSql(), bs.getParams(), bs.getTypes())
                : jdbcTemplate.update(bs.getSql(), bs.getParams());
    }

    /**
     * 合并默认过滤器
     */
    private void applyDefaultFilter(FilterGroup filterGroup) {
        if (defaultFilterOption && defaultFilterGroup != null) {
            for (FilterGroup.Filter filter : defaultFilterGroup.getFilters()) {
                filterGroup.addFilter(filter);
            }
        }
    }

    /**
     * 带 types 支持的 Long 查询，用于 count 场景
     */
    private Long queryForLong(String sql, Object[] params, int[] types) {
        if (types != null && types.length > 0) {
            List<Long> result = jdbcTemplate.query(sql,
                    ps -> { for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i], types[i]); },
                    (rs, rowNum) -> rs.getLong(1));
            return result.isEmpty() ? 0L : result.get(0);
        }
        return jdbcTemplate.queryForObject(sql, Long.class, params);
    }

    /**
     * model 级查询公共逻辑：生成 SQL + 执行 + 异常转换
     */
    private <T> T queryForObjectWithFilter(Class<T> entityType, FilterGroup fg, String orderBy) {
        BoundSql bs = sqlManager.generateQueryForObjectOrMapSql(entityType, fg, orderBy);
        return execute(bs, () -> jdbcTemplate.queryForObject(bs.getSql(), bs.getParams(), new CommonRowMapper<>()));
    }

    /**
     * C2：触发查询前事件（无监听器时 fireBefore 内部快速返回，零开销）。
     */
    private QueryEventContext fireQueryBefore(BoundSql bs, Class<?> entityType) {
        QueryEventContext ctx = new QueryEventContext(this, new SessionCtx(), bs,
                bs.getCommand() instanceof cn.geelato.core.mql.command.QueryCommand
                        ? (cn.geelato.core.mql.command.QueryCommand) bs.getCommand() : null,
                entityType);
        QueryEventManager.fireBefore(ctx);
        return ctx;
    }

    /** C2：触发查询后事件。 */
    private void fireQueryAfter(QueryEventContext ctx, int rowCount, boolean success, Throwable ex) {
        if (ctx == null) {
            return;
        }
        ctx.setRowCount(rowCount);
        ctx.setSuccess(success);
        ctx.setException(ex);
        QueryEventManager.fireAfter(ctx);
    }

    private List<Map<String, Object>> queryForMapListWithFilter(Class entityType, FilterGroup fg) {
        BoundSql bs = sqlManager.generateQueryForListSql(entityType, fg, null);
        // C2：查询事件触发（无监听器时快速返回，零开销）
        QueryEventContext qCtx = fireQueryBefore(bs, entityType);
        try {
            List<Map<String, Object>> result = execute(bs, () -> jdbcTemplate.queryForList(bs.getSql(), bs.getParams()));
            fireQueryAfter(qCtx, result == null ? 0 : result.size(), true, null);
            return result;
        } catch (RuntimeException ex) {
            fireQueryAfter(qCtx, 0, false, ex);
            throw ex;
        }
    }

    /**
     * @Ignore 注解缓存，避免每次分页查询都做反射
     */
    private static final Map<Class<?>, Map<IgnoreType, String[]>> IGNORE_FIELDS_CACHE = new ConcurrentHashMap<>();


    //========================================================
    //                  基于元数据  gql                      ==
    //========================================================
    public Map<String, Object> queryForMap(BoundSql boundSql) throws DataAccessException {
        return execute(boundSql, () -> {
            List<Map<String, Object>> rows = boundSql.getTypes() != null && boundSql.getTypes().length > 0
                    ? jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), new DecryptingRowMapper())
                    : jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), new DecryptingRowMapper());
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) throws DataAccessException {
        return execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.queryForObject(boundSql.getSql(), requiredType, boundSql.getParams(), boundSql.getTypes())
                : jdbcTemplate.queryForObject(boundSql.getSql(), requiredType, boundSql.getParams()));
    }

    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        QueryCommand command = (QueryCommand) boundPageSql.getBoundSql().getCommand();
        return convert(queryForMapListInner(boundPageSql.getBoundSql()), metaManager.getByEntityName(command.getEntityName()));
    }

    public List<Map<String, Object>> callForMapList(String callSql, Object[] params) {
        try {
            return jdbcTemplate.query(callSql, new DecryptingRowMapper(), params);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, callSql, params);
        }
    }

    public Map<String, Object> callForMap(String callSql, Object[] params) {
        List<Map<String, Object>> rows = callForMapList(callSql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params) {
        try {
            return jdbcTemplate.query(sql, new DecryptingRowMapper(), params);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, sql, params);
        }
    }

    public Map<String, Object> nativeQueryForMap(String sql, Object[] params) {
        List<Map<String, Object>> rows = nativeQueryForMapList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType) {
        try {
            return jdbcTemplate.queryForObject(sql, requiredType, params);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, sql, params);
        }
    }

    public int nativeExecute(String sql, Object[] params) {
        try {
            return jdbcTemplate.update(sql, params);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, sql, params);
        }
    }

    public Long queryTotal(BoundPageSql boundPageSql) {
        BoundSql boundSql = boundPageSql.getBoundSql();
        return queryForLong(boundPageSql.getCountSql(), boundSql.getParams(), boundSql.getTypes());
    }

    private List<Map<String, Object>> convert(List<Map<String, Object>> data, EntityMeta entityMeta) {
        for (Map<String, Object> map : data) {
            for (String key : map.keySet()) {
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);
                if (fieldMeta != null) {
                    String columnType = entityMeta.getFieldMeta(key).getColumnMeta().getDataType();
                    if ("JSON".equals(columnType)) {
                        Object value = map.get(key);
                        String str = (value != null) ? value.toString() : "";
                        if (str.startsWith("{") && str.endsWith("}")) {
                            JSONObject jsonObject = JSONObject.parse(value.toString());
                            map.replace(key, value, jsonObject);
                        } else if (str.startsWith("[") && str.endsWith("]")) {
                            JSONArray jsonArray = JSONArray.parse(value.toString());
                            map.replace(key, value, jsonArray);
                        }
                    }
                }
            }
        }
        return data;
    }

 

    public <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType) {
        return execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.queryForList(boundSql.getSql(), elementType, boundSql.getParams(), boundSql.getTypes())
                : jdbcTemplate.queryForList(boundSql.getSql(), elementType, boundSql.getParams()));
    }

    /**
     * 保存
     *
     * @param boundSql 查询语句
     * @return 主健值
     */

    public String save(BoundSql boundSql) {
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        SessionCtx sessionCtx = new SessionCtx();
        SaveEventContext context = new SaveEventContext(this, sessionCtx, null, boundSql, command);
        SaveEventManager.fireBefore(context);
        try {
            executeVoid(context.getBoundSql(), () -> updateWithTypes(context.getBoundSql()));
        } catch (RuntimeException ex) {
            // A1：失败时回填执行结果，before/after 调用方据此判定
            context.setSuccess(false);
            context.setException(ex);
            throw ex;
        }
        context.setSuccess(true);
        context.setResultValueMap(command.getValueMap());
        SaveEventManager.fireAfter(context);
        // A2：触发事务感知回调（有事务则提交后执行，无事务立即执行）
        cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        return command.getPK();
    }

    /**
     * 批量保存
     */
    public List<String> batchSave(List<BoundSql> boundSqlList) {
        List<Object[]> paramsObjs = new ArrayList<>();
        List<String> returnPks = new ArrayList<>();
        List<SaveEventContext> contexts = new ArrayList<>();
        SessionCtx sessionCtx = new SessionCtx();
        for (BoundSql bs : boundSqlList) {
            paramsObjs.add(bs.getParams());
            SaveCommand saveCommand = (SaveCommand) bs.getCommand();
            returnPks.add(saveCommand.getPK());
            SaveEventContext context = new SaveEventContext(this, sessionCtx, null, bs, saveCommand);
            contexts.add(context);
            SaveEventManager.fireBefore(context);
        }
        try {
            jdbcTemplate.batchUpdate(boundSqlList.get(0).getSql(), paramsObjs);
        } catch (DataAccessException dataAccessException) {
            // A1：失败时回填执行结果
            for (SaveEventContext context : contexts) {
                context.setSuccess(false);
                context.setException(dataAccessException);
            }
            throw new SqlExecuteException(dataAccessException, boundSqlList.get(0).getSql());
        }
        for (SaveEventContext context : contexts) {
            context.setSuccess(true);
            context.setResultValueMap(context.getCommand().getValueMap());
            SaveEventManager.fireAfter(context);
            // A2：触发事务感知回调
            cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        }
        return returnPks;
    }

    public List<String> multiSave(List<BoundSql> boundSqlList) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(this.jdbcTemplate.getDataSource());
        TransactionStatus transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
        List<String> returnPks = new ArrayList<>();
        for (BoundSql bs : boundSqlList) {
            SaveCommand saveCommand = (SaveCommand) bs.getCommand();
            returnPks.add(saveCommand.getPK());
            SessionCtx sessionCtx = new SessionCtx();
            SaveEventContext context = new SaveEventContext(this, sessionCtx, null, bs, saveCommand);
            try {
                // A3：fireBefore 纳入 try，确保 before 监听器抛非 DataAccessException 时也能回滚、事务不悬挂
                SaveEventManager.fireBefore(context);
                updateWithTypes(bs);
            } catch (RuntimeException ex) {
                // A1：回填执行结果
                context.setSuccess(false);
                context.setException(ex);
                TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                returnPks.clear();
                if (ex instanceof DataAccessException) {
                    throw new SqlExecuteException((DataAccessException) ex, bs.getSql(), bs.getParams());
                }
                throw ex;
            }
            context.setSuccess(true);
            context.setResultValueMap(saveCommand.getValueMap());
            SaveEventManager.fireAfter(context);
            // A2：触发事务感知回调（此时事务尚未提交，trigger 会登记到 commit/rollback 点）
            cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        }
        TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        return returnPks;
    }

    /**
     * 删除
     *
     * @param boundSql 删除语句
     * @return 删除的记录数据
     */
    public int delete(BoundSql boundSql) {
        DeleteCommand command = (DeleteCommand) boundSql.getCommand();
        SessionCtx sessionCtx = new SessionCtx();
        DeleteEventContext context = new DeleteEventContext(this, sessionCtx, boundSql, command);
        DeleteEventManager.fireBefore(context);
        int n;
        try {
            n = updateWithTypes(context.getBoundSql());
        } catch (RuntimeException ex) {
            // A1：回填执行结果
            context.setSuccess(false);
            context.setException(ex);
            throw ex;
        }
        context.setSuccess(true);
        context.setAffectedRows(n);
        DeleteEventManager.fireAfter(context);
        // A2：触发事务感知回调
        cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        return n;
    }

    /**
     * 批量事务删除
     *
     * @param boundSqlList 删除语句列表
     * @return 受影响的总行数
     */
    public int multiDelete(List<BoundSql> boundSqlList) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(this.jdbcTemplate.getDataSource());
        TransactionStatus transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
        int totalAffected = 0;
        for (BoundSql bs : boundSqlList) {
            DeleteCommand command = (DeleteCommand) bs.getCommand();
            SessionCtx sessionCtx = new SessionCtx();
            DeleteEventContext context = new DeleteEventContext(this, sessionCtx, bs, command);
            int n;
            try {
                // A3：fireBefore 纳入 try，确保 before 监听器异常也能回滚
                DeleteEventManager.fireBefore(context);
                n = updateWithTypes(bs);
            } catch (RuntimeException ex) {
                // A1：回填执行结果
                context.setSuccess(false);
                context.setException(ex);
                TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                if (ex instanceof DataAccessException) {
                    throw new SqlExecuteException((DataAccessException) ex, bs.getSql(), bs.getParams());
                }
                throw ex;
            }
            context.setSuccess(true);
            context.setAffectedRows(n);
            totalAffected += n;
            DeleteEventManager.fireAfter(context);
            // A2：触发事务感知回调
            cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        }
        TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        return totalAffected;
    }

    public void executeUpdate(BoundSql boundSql) {
        executeVoid(boundSql, () -> updateWithTypes(boundSql));
    }

    public Map<String, Object> queryByEntityNameAndPK(String entityName, Object pkValue) {
        EntityMeta meta = metaManager.getByEntityName(entityName);
        String idFieldName = meta.getId().getFieldName();
        return queryForMap(meta.getClassType(), idFieldName, pkValue);
    }


    //========================================================
    //                  基于元数据  model                   ==
    //========================================================

    /**
     * 依据主键查询实体
     *
     * @param entityType 实体类型
     * @return 单个实体
     */
    public <T> T queryForObject(Class<T> entityType, Object PKValue) {
        return queryForObject(entityType, metaManager.get(entityType).getId().getFieldName(), PKValue);
    }

    /**
     * 依据单个条件查询实体
     *
     * @param entityType 实体类型
     * @param fieldName  实体的属性名
     * @param value      实体属性的值
     * @return 单个实体
     */
    public <T> T queryForObject(Class<T> entityType, String fieldName, Object value) {
        return queryForObjectWithFilter(entityType, new FilterGroup().addFilter(fieldName, value.toString()), null);
    }

    /**
     * 依据实体对象中的有效属性查询单个实体
     *
     * @param entityType 实体类型
     * @param example    查询样例对象
     * @return 单个实体
     */
    public <T extends IdEntity> T queryForObject(Class<T> entityType, T example) {
        if (example == null) {
            throw new IllegalArgumentException("example不能为空");
        }
        EntityMeta entityMeta = metaManager.get(entityType);
        FilterGroup filterGroup = new FilterGroup();
        for (String fieldName : entityMeta.getFieldNames()) {
            addExampleFilter(filterGroup, fieldName, getFieldValue(entityType, example, fieldName));
        }
        if (filterGroup.getFilters().isEmpty() && filterGroup.getChildFilterGroup().isEmpty()) {
            throw new IllegalArgumentException("example中没有有效的查询条件");
        }
        return queryForObjectWithFilter(entityType, filterGroup, null);
    }

    /**
     * 依据两个条件查询实体
     *
     * @param entityType 实体类型
     * @param fieldName1 实体的属性名1
     * @param value1     实体属性1的值
     * @param fieldName2 实体的属性名2
     * @param value2     实体属性2的值
     * @return 返回泛型
     */
    public <T> T queryForObject(Class<T> entityType, String fieldName1, Object value1, String fieldName2, Object value2) {
        return queryForObjectWithFilter(entityType,
                new FilterGroup().addFilter(fieldName1, value1.toString()).addFilter(fieldName2, value2.toString()), null);
    }

    /**
     * 依据单个条件查询实体
     *
     * @param entityType 实体类型
     * @param fieldName  实体的属性名
     * @param value      实体属性的值
     * @return map通用格式的实体信息
     */
    public Map queryForMap(Class entityType, String fieldName, Object value) {
        BoundSql bs = sqlManager.generateQueryForObjectOrMapSql(entityType, new FilterGroup().addFilter(fieldName, value.toString()), null);
        return execute(bs, () -> jdbcTemplate.queryForMap(bs.getSql(), bs.getParams()));
    }

    /**
     * 依据单个条件查询多个实体
     *
     * @param entityType 实体类型
     * @param fieldName  实体的属性名
     * @param value      实体属性的值
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType, String fieldName, Object value) {
        return queryForMapListWithFilter(entityType, new FilterGroup().addFilter(fieldName, value.toString()));
    }

    /**
     * 依据多个条件查询多个实体
     *
     * @param entityType  实体类型
     * @param filterGroup 多条件过滤组合
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType, FilterGroup filterGroup) {
        return queryForMapListWithFilter(entityType, filterGroup);
    }

    /**
     * 无过滤条件查询多个实体
     *
     * @param entityType 实体类型
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType) {
        return queryForMapListWithFilter(entityType, null);
    }


    /**
     * 保存实体：按 id 自动判定插入或更新——id 非空即 Update，id 为空即 Insert（自动生成主键）。
     * 需要显式控制语句类型时（如指定 id 插入），请使用 {@link #insert(IdEntity)} 或 {@link #update(IdEntity)}。
     */
    public <E extends IdEntity> Map save(E entity) {
        return doSave(entity, null);
    }

    /**
     * 插入实体：始终构造 INSERT 语句。实体已带 id 时保留该 id（用于指定主键的插入），id 为空时自动生成。
     */
    public <E extends IdEntity> Map insert(E entity) {
        return doSave(entity, CommandType.Insert);
    }

    /**
     * 更新实体：始终构造 UPDATE 语句，以实体的 id 作为 where 条件；id 为空时抛出 IllegalArgumentException。
     */
    public <E extends IdEntity> Map update(E entity) {
        return doSave(entity, CommandType.Update);
    }

    private <E extends IdEntity> Map doSave(E entity, CommandType forcedType) {
        SessionCtx sessionCtx = new SessionCtx();
        BoundSql boundSql = entityManager.generateSaveSql(entity, sessionCtx, forcedType);
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        SaveEventContext context = new SaveEventContext(this, sessionCtx, entity, boundSql, command);
        SaveEventManager.fireBefore(context);
        try {
            executeVoid(context.getBoundSql(), () -> updateWithTypes(context.getBoundSql()));
        } catch (RuntimeException ex) {
            context.setSuccess(false);
            context.setException(ex);
            throw ex;
        }
        context.setSuccess(true);
        Map<String, Object> valueMap = command.getValueMap();
        context.setResultValueMap(valueMap);
        SaveEventManager.fireAfter(context);
        cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        return valueMap;
    }


    /**
     * 全量查询，使用特殊的查询条件。
     * 根据指定的实体类型、过滤条件和排序规则，执行全量查询操作。
     *
     * @param entityType  要查询的实体类型
     * @param filterGroup 过滤条件组，包含多个过滤条件
     * @param orderBy     排序规则，用于指定查询结果的排序方式
     * @param <T>         泛型类型，表示查询结果的数据类型
     * @return 返回查询结果列表，列表中的元素类型为T
     */
    public <T> List<T> queryList(Class<T> entityType, FilterGroup filterGroup, String orderBy) {
        applyDefaultFilter(filterGroup);
        BoundSql bs = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, orderBy);
        // C2：查询事件触发
        QueryEventContext qCtx = fireQueryBefore(bs, entityType);
        try {
            List<T> result = execute(bs, () -> jdbcTemplate.query(bs.getSql(), new CommonRowMapper<>(), bs.getParams()));
            fireQueryAfter(qCtx, result == null ? 0 : result.size(), true, null);
            return result;
        } catch (RuntimeException ex) {
            fireQueryAfter(qCtx, 0, false, ex);
            throw ex;
        }
    }

    /**
     * 常用全量查询，执行全等式查询。
     * 根据提供的实体类型、查询参数和排序规则，执行全量查询并返回结果列表。
     *
     * @param entityType 要查询的实体类型
     * @param params     查询参数，以键值对的形式提供
     * @param orderBy    排序规则，指定返回结果的排序方式
     * @param <T>        泛型参数，表示查询结果的类型
     * @return 返回查询结果的列表
     */
    public <T> List<T> queryList(Class<T> entityType, Map<String, Object> params, String orderBy) {
        FilterGroup filterGroup = generateFilterGroup(params);
        return queryList(entityType, filterGroup, orderBy);
    }

    /**
     * 分页查询，支持特殊条件查询。
     * 根据给定的实体类型、过滤器组、分页查询请求，执行分页查询并返回查询结果列表。
     *
     * @param entityType  要查询的实体类型
     * @param filterGroup 包含查询条件的过滤器组
     * @param request     分页查询请求，包含分页信息、排序信息等
     * @param <T>         实体类型，泛型参数，表示查询结果的数据类型
     * @return 查询结果列表，元素类型为T
     */
    public <T> List<T> pageQueryList(Class<T> entityType, FilterGroup filterGroup, PageQueryRequest request) {
        applyDefaultFilter(filterGroup);
        QueryCommand command = new QueryCommand();
        command.setPageNum(request.getPageNum());
        command.setPageSize(request.getPageSize());
        command.setOrderBy(request.getOrderBy());
        command.setIgnoreFields(getIgnoreFields(entityType, IgnoreType.PAGE_QUERY));
        BoundSql bs = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        return execute(bs, () -> jdbcTemplate.query(bs.getSql(), new CommonRowMapper<T>(), bs.getParams()));
    }

    public <T> ApiPagedResult pageQueryResult(Class<T> entityType, FilterGroup filterGroup, PageQueryRequest request) {
        applyDefaultFilter(filterGroup);
        QueryCommand command = new QueryCommand();
        command.setPageNum(request.getPageNum());
        command.setPageSize(request.getPageSize());
        command.setOrderBy(request.getOrderBy());
        command.setIgnoreFields(getIgnoreFields(entityType, IgnoreType.PAGE_QUERY));
        BoundSql bs = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        BoundSql countBs = sqlManager.generateCountSql(command, entityType, filterGroup);
        try{
            List<T> pageQueryList = jdbcTemplate.query(bs.getSql(), new CommonRowMapper<>(), bs.getParams());
            long total = queryForLong(countBs.getSql(), countBs.getParams(), countBs.getTypes());
            int dataSize = pageQueryList.size();
            return ApiPagedResult.success(new DataItems<>(pageQueryList, total), request.getPageNum(), request.getPageSize(), dataSize, total);
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, bs.getSql(), bs.getParams());
        }
    }

    public <T> ApiPagedResult pageQueryResult(Class<T> entityType, Map<String, Object> params, PageQueryRequest request) {
        FilterGroup filterGroup = generateFilterGroup(params);
        return pageQueryResult(entityType, filterGroup, request);
    }

    private FilterGroup generateFilterGroup(Map<String, Object> params) {
        FilterGroup filterGroup = new FilterGroup();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null && Strings.isNotBlank(entry.getValue().toString())) {
                    filterGroup.addFilter(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        return filterGroup;
    }

    private void addExampleFilter(FilterGroup filterGroup, String fieldName, Object value) {
        if (value == null) {
            return;
        }
        if ("delStatus".equals(fieldName)) {
            if (isZeroNumber(value)) {
                FilterGroup delStatusGroup = new FilterGroup(FilterGroup.Logic.or);
                delStatusGroup.addFilter(fieldName, FilterGroup.Operator.neq, String.valueOf(cn.geelato.core.enums.DeleteStatusEnum.IS.getValue()));
                delStatusGroup.addFilter(fieldName, FilterGroup.Operator.nil, "true");
                filterGroup.getChildFilterGroup().add(delStatusGroup);
                return;
            }
            filterGroup.addFilter(fieldName, String.valueOf(value), value);
            return;
        }
        if (value instanceof String str) {
            if (Strings.isBlank(str)) {
                return;
            }
            filterGroup.addFilter(fieldName, str);
            return;
        }
        if (value instanceof Number && isZeroNumber(value)) {
            return;
        }
        filterGroup.addFilter(fieldName, String.valueOf(value), value);
    }

    private Object getFieldValue(Class<?> entityType, Object target, String fieldName) {
        Field field = getField(entityType, fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取字段值失败: " + fieldName, e);
        }
    }

    private Field getField(Class<?> entityType, String fieldName) {
        Class<?> currentType = entityType;
        while (currentType != null) {
            try {
                return currentType.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentType = currentType.getSuperclass();
            }
        }
        return null;
    }

    private boolean isZeroNumber(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        if (number instanceof BigDecimal bigDecimal) {
            return BigDecimal.ZERO.compareTo(bigDecimal) == 0;
        }
        if (number instanceof BigInteger bigInteger) {
            return BigInteger.ZERO.compareTo(bigInteger) == 0;
        }
        return number.doubleValue() == 0D;
    }

    public List<Map<String, Object>> queryListByView(String entityName, String viewName, int pageNum, int pageSize, Map<String, Object> params) {
        FilterGroup filterGroup = new FilterGroup();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null && Strings.isNotBlank(entry.getValue().toString())) {
                filterGroup.addFilter(entry.getKey(), entry.getValue().toString());
            }
        }
        QueryViewCommand command = new QueryViewCommand();
        command.setPageNum(pageNum);
        command.setPageSize(pageSize);
        command.setViewName(viewName);
        BoundSql boundSql = sqlManager.generatePageQuerySql(command, entityName, true, filterGroup, null);
        try{
            return jdbcTemplate.queryForList(boundSql.getSql());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    public int delete(Class entityType, String fieldName, Object value) {
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName, value.toString());
        BoundSql boundSql = sqlManager.generateDeleteSql(entityType, filterGroup);
        DeleteCommand command = (DeleteCommand) boundSql.getCommand();
        SessionCtx sessionCtx = new SessionCtx();
        DeleteEventContext context = new DeleteEventContext(this, sessionCtx, boundSql, command);
        DeleteEventManager.fireBefore(context);
        int n;
        try {
            n = updateWithTypes(context.getBoundSql());
        } catch (RuntimeException ex) {
            // A1：回填执行结果
            context.setSuccess(false);
            context.setException(ex);
            throw ex;
        }
        context.setSuccess(true);
        context.setAffectedRows(n);
        DeleteEventManager.fireAfter(context);
        // A2：触发事务感知回调
        cn.geelato.core.orm.event.EventTransactionSupport.trigger(context);
        return n;
    }

    /**
     * 根据实体类型和忽略类型获取需要忽略的字段数组
     *
     * @param entityType 实体类型
     * @param ignoreType 忽略类型
     * @return 需要忽略的字段数组，若不存在则返回null
     */
    private String[] getIgnoreFields(Class entityType, IgnoreType ignoreType) {
        return getIgnoreFields(entityType).get(ignoreType);
    }

    /**
     * 根据实体类型获取需要忽略的字段（带缓存）
     */
    private Map<IgnoreType, String[]> getIgnoreFields(Class entityType) {
        return IGNORE_FIELDS_CACHE.computeIfAbsent(entityType, this::resolveIgnoreFields);
    }

    private Map<IgnoreType, String[]> resolveIgnoreFields(Class entityType) {
        Map<String, IgnoreType[]> fieldIgnores = new HashMap<>();
        for (Class<?> searchType = entityType; searchType != Object.class; searchType = searchType.getSuperclass()) {
            for (Field field : searchType.getDeclaredFields()) {
                Ignore ig = field.getAnnotation(Ignore.class);
                if (ig != null && ig.type().length > 0) {
                    fieldIgnores.put(field.getName(), ig.type());
                }
            }
        }
        Map<IgnoreType, List<String>> ignoreFieldList = new HashMap<>();
        for (Map.Entry<String, IgnoreType[]> entry : fieldIgnores.entrySet()) {
            for (IgnoreType value : entry.getValue()) {
                ignoreFieldList.computeIfAbsent(value, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return ignoreFieldList.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray(new String[0])));
    }
}
