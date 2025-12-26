package cn.geelato.core.orm;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.command.QueryViewCommand;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author geemeta
 */
@Slf4j
@SuppressWarnings("rawtypes")
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
    //                  基于元数据  gql                      ==
    //========================================================
    public Map<String, Object> queryForMap(BoundSql boundSql) throws DataAccessException {
        try{
            return jdbcTemplate.queryForMap(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }

    }

    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) throws DataAccessException {
        try{
            return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), requiredType);
        }catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        log.info(boundPageSql.getBoundSql().getSql());
        QueryCommand command = (QueryCommand) boundPageSql.getBoundSql().getCommand();
        BoundSql boundSql = boundPageSql.getBoundSql();
        List<Map<String, Object>> result;
        List<Map<String, Object>> list = queryForMapListInner(boundSql);
        result = convert(list, metaManager.getByEntityName(command.getEntityName()));
        return result;
    }

    public Long queryTotal(BoundPageSql boundPageSql) {
        BoundSql boundSql = boundPageSql.getBoundSql();
        Object[] sqlParams = boundSql.getParams();
        Long total;
        try {
            total = jdbcTemplate.queryForObject(boundPageSql.getCountSql(), sqlParams, Long.class);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
        return total;
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
        try{
            return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams(), elementType);
        }catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    /**
     * 保存
     *
     * @param boundSql 查询语句
     * @return 主健值
     */

    public String save(BoundSql boundSql) {
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        try {
            jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
            return command.getPK();
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    /**
     * 批量保存
     */
    public List<String> batchSave(List<BoundSql> boundSqlList) {
        List<Object[]> paramsObjs = new ArrayList<>();
        List<String> returnPks = new ArrayList<>();
        for (BoundSql bs : boundSqlList) {
            paramsObjs.add(bs.getParams());
            SaveCommand saveCommand = (SaveCommand) bs.getCommand();
            returnPks.add(saveCommand.getPK());
        }
        try {
            jdbcTemplate.batchUpdate(boundSqlList.get(0).getSql(), paramsObjs);
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSqlList.get(0).getSql());
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
            try {
                jdbcTemplate.update(bs.getSql(), bs.getParams());
            } catch (DataAccessException dataAccessException) {
                TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
                returnPks.clear();
                throw new SqlExecuteException(dataAccessException, bs.getSql(), bs.getParams());
            }
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
        try {
            return jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
        } catch (DataAccessException dataAccessException) {
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
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
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName, value.toString());
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, null);
        try{
            return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), new CommonRowMapper<>());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
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
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName1, value1.toString()).addFilter(fieldName2, value2.toString());
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, null);
        try{
            return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), new CommonRowMapper<T>());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
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
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName, value.toString());
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, null);
        try{
            return jdbcTemplate.queryForMap(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }

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
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName, value.toString());
        return queryForMapList(entityType, filterGroup);
    }

    /**
     * 依据多个条件查询多个实体
     *
     * @param entityType  实体类型
     * @param filterGroup 多条件过滤组合
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType, FilterGroup filterGroup) {
        BoundSql boundSql = sqlManager.generateQueryForListSql(entityType, filterGroup, null);
        try{
            return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    /**
     * 无过滤条件查询多个实体
     *
     * @param entityType 实体类型
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType) {
        BoundSql boundSql = sqlManager.generateQueryForListSql(entityType, null, null);
        try{
            return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }


    /**
     * @param entityType  查询的实体
     * @param field       查询的单列字段
     * @param filterGroup 查询条件
     * @param <T>         数据列类型
     * @return 单列数据列表
     */
    public <T> List<T> queryForOneColumnList(Class<T> entityType, String field, FilterGroup filterGroup) {
        BoundSql boundSql = sqlManager.generateQueryForListSql(entityType, filterGroup, field);
        try{
            return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams(), entityType);
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }


    public <E extends IdEntity> Map save(E entity) {
        BoundSql boundSql = entityManager.generateSaveSql(entity, new SessionCtx());
        try{
            jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        return command.getValueMap();
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
        if (defaultFilterOption && defaultFilterGroup != null) {
            for (FilterGroup.Filter filter : defaultFilterGroup.getFilters()) {
                filterGroup.addFilter(filter);
            }
        }
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, orderBy);
        try{
            return jdbcTemplate.query(boundSql.getSql(), new CommonRowMapper<>(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
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
        if (defaultFilterOption && defaultFilterGroup != null) {
            for (FilterGroup.Filter filter : defaultFilterGroup.getFilters()) {
                filterGroup.addFilter(filter);
            }
        }
        QueryCommand command = new QueryCommand();
        command.setPageNum(request.getPageNum());
        command.setPageSize(request.getPageSize());
        command.setOrderBy(request.getOrderBy());
        String[] ignoreFields = getIgnoreFields(entityType, IgnoreType.PAGE_QUERY);
        command.setIgnoreFields(ignoreFields);
        BoundSql boundSql = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        try{
            return jdbcTemplate.query(boundSql.getSql(), new CommonRowMapper<T>(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    public <T> ApiPagedResult pageQueryResult(Class<T> entityType, FilterGroup filterGroup, PageQueryRequest request) {
        if (defaultFilterOption && defaultFilterGroup != null) {
            for (FilterGroup.Filter filter : defaultFilterGroup.getFilters()) {
                filterGroup.addFilter(filter);
            }
        }
        QueryCommand command = new QueryCommand();
        command.setOrderBy(request.getOrderBy());
        String[] ignoreFields = getIgnoreFields(entityType, IgnoreType.PAGE_QUERY);
        command.setIgnoreFields(ignoreFields);
        BoundSql boundSql = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        log.info(boundSql.toString());
        List<T> queryList = jdbcTemplate.query(boundSql.getSql(),
                new CommonRowMapper<>(), boundSql.getParams());
        command.setPageNum(request.getPageNum());
        command.setPageSize(request.getPageSize());
        boundSql = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        try{
            List<T> pageQueryList = jdbcTemplate.query(boundSql.getSql(),
                    new CommonRowMapper<>(), boundSql.getParams());
            long total = queryList.size();
            int dataSize = pageQueryList.size();
            return ApiPagedResult.success(new DataItems<>(pageQueryList, total), request.getPageNum(), request.getPageSize(), dataSize, total);
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
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
        try{
            return jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }
    }

    /**
     * 根据实体类型和忽略类型获取需要忽略的字段数组
     *
     * @param entityType 实体类型
     * @param ignoreType 忽略类型
     * @return 需要忽略的字段数组，若不存在则返回null
     */
    private String[] getIgnoreFields(Class entityType, IgnoreType ignoreType) {
        Map<IgnoreType, String[]> ignoreFields = getIgnoreFields(entityType);
        return ignoreFields.get(ignoreType);
    }

    /**
     * 根据实体类型获取需要忽略的字段
     *
     * @param entityType 实体类型
     * @return 需要忽略的字段的Map，键为忽略类型，值为字段数组
     */
    private Map<IgnoreType, String[]> getIgnoreFields(Class entityType) {
        Map<String, IgnoreType[]> fieldIgnores = new HashMap<>();
        // 获取所有字段上的@Ignore注解
        for (Class<?> searchType = entityType; searchType != Object.class; searchType = searchType.getSuperclass()) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                Ignore ig = field.getAnnotation(Ignore.class);
                if (ig != null && ig.type().length > 0) {
                    fieldIgnores.put(field.getName(), ig.type());
                }
            }
        }
        // 将@Ignore注解中指定的类型和字段名转换为Map
        Map<IgnoreType, List<String>> ignoreFieldList = new HashMap<>();
        for (Map.Entry<String, IgnoreType[]> entry : fieldIgnores.entrySet()) {
            String key = entry.getKey();
            IgnoreType[] values = entry.getValue();
            for (IgnoreType value : values) {
                ignoreFieldList.computeIfAbsent(value, k -> new ArrayList<>()).add(key);
            }
        }

        return ignoreFieldList.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray(new String[0])));
    }
}
