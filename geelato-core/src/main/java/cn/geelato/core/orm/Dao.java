package cn.geelato.core.orm;

import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.command.QueryViewCommand;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.*;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.Ctx;
import cn.geelato.core.aop.annotation.MethodLog;
import cn.geelato.lang.api.ApiMultiPagedResult;
import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.meta.model.CommonRowMapper;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.field.FieldMeta;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
public class Dao extends SqlKeyDao {


    private Boolean defaultFilterOption = false;
    private FilterGroup defaultFilterGroup;

    /**
     * <p>注意: 在使用之前，需先设置JdbcTemplate
     */
    public Dao() {
    }

    public Dao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
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
        return jdbcTemplate.queryForMap(boundSql.getSql(), boundSql.getParams());
    }

    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) throws DataAccessException {
        return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), requiredType);
    }

    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        log.info(boundPageSql.getBoundSql().getSql());
        QueryCommand command = (QueryCommand) boundPageSql.getBoundSql().getCommand();
        BoundSql boundSql = boundPageSql.getBoundSql();
        Object[] sqlParams = boundSql.getParams();
        List<Map<String, Object>> result;
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(boundSql.getSql(), sqlParams);
            result=convert(list, metaManager.getByEntityName(command.getEntityName()));
        } catch (DataAccessException exception) {
            throw new DaoException("queryForMapList exception :" + exception.getCause().getMessage());
        }
        return result;
    }

    public Long queryTotal(BoundPageSql boundPageSql){
        BoundSql boundSql = boundPageSql.getBoundSql();
        Object[] sqlParams = boundSql.getParams();
        Long total=0L;
        try {
            total = jdbcTemplate.queryForObject(boundPageSql.getCountSql(), sqlParams, Long.class);
        } catch (DataAccessException exception) {
            throw new DaoException("queryForMapList exception :" + exception.getCause().getMessage());
        }
        return total;
    }
    private List<Map<String, Object>> convert(List<Map<String, Object>> data, EntityMeta entityMeta) {
        for (Map<String, Object> map : data) {
            for (String key : map.keySet()) {
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);
                if (fieldMeta != null) {
                    String columnType = entityMeta.getFieldMeta(key).getColumn().getDataType();
                    if (columnType.equals("JSON")) {
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

    /**
     * @param withMeta 是否需同时查询带出元数据
     */
    //todo rewrite
    public ApiMultiPagedResult.PageData queryForMapListToPageData(BoundPageSql boundPageSql, boolean withMeta) {
        QueryCommand command = (QueryCommand) boundPageSql.getBoundSql().getCommand();
        log.info(boundPageSql.getBoundSql().getSql());
        List<Map<String, Object>> list = jdbcTemplate.queryForList(boundPageSql.getBoundSql().getSql(), boundPageSql.getBoundSql().getParams());
        ApiMultiPagedResult.PageData result = new ApiMultiPagedResult.PageData();
        result.setData(list);
        result.setTotal(jdbcTemplate.queryForObject(boundPageSql.getCountSql(), boundPageSql.getBoundSql().getParams(), Long.class));
        result.setPage(command.getPageNum());
        result.setSize(command.getPageSize());
        result.setDataSize(list.size());
        if (withMeta) {
            result.setMeta(metaManager.getByEntityName(command.getEntityName()).getSimpleFieldMetas(command.getFields()));
        }
        return result;
    }

    public <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType) throws DataAccessException {
        return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams(), elementType);
    }

    /**
     * 保存
     *
     * @param boundSql 查询语句
     * @return 主健值
     */
    public String save(BoundSql boundSql) throws DaoException {
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        try {
            log.info(boundSql.getSql());
            jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
        } catch (DataAccessException e) {
            e.printStackTrace();
            throw new DaoException("dao exception:" + e.getMessage());
        }
        return command.getPK();
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
        } catch (DataAccessException e) {
            e.printStackTrace();
        }
        return returnPks;
    }

    public List<String> multiSave(List<BoundSql> boundSqlList) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(this.jdbcTemplate.getDataSource());
        TransactionStatus transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
        List<String> returnPks = new ArrayList<>();
        try {
            for (BoundSql bs : boundSqlList) {
                SaveCommand saveCommand = (SaveCommand) bs.getCommand();
                returnPks.add(saveCommand.getPK());
                jdbcTemplate.update(bs.getSql(), bs.getParams());
            }
            TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        } catch (DataAccessException e) {
            e.printStackTrace();
            TransactionHelper.rollbackTransaction(dataSourceTransactionManager, transactionStatus);
            returnPks.clear();
        }
        return returnPks;
    }

    /**
     * 删除
     *
     * @param boundSql 删除语句
     * @return 删除的记录数据
     */
    public int delete(BoundSql boundSql) {
        return jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
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
        log.info(boundSql.toString());
        return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), new CommonRowMapper<T>());
    }

    /**
     * 依据两个条件查询实体
     * @param entityType 实体类型
     * @param fieldName1 实体的属性名1
     * @param value1     实体属性1的值
     * @param fieldName2 实体的属性名2
     * @param value2     实体属性2的值
     * @return           返回泛型
     */
    public <T> T queryForObject(Class<T> entityType, String fieldName1, Object value1, String fieldName2, Object value2) {
        FilterGroup filterGroup = new FilterGroup().addFilter(fieldName1, value1.toString()).addFilter(fieldName2, value2.toString());
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, null);
        log.info(boundSql.toString());
        return jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), new CommonRowMapper<T>());
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
        return jdbcTemplate.queryForMap(boundSql.getSql(), boundSql.getParams());
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
        return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams());
    }

    /**
     * 无过滤条件查询多个实体
     *
     * @param entityType 实体类型
     * @return map通用格式的实体信息列表
     */
    public List<Map<String, Object>> queryForMapList(Class entityType) {
        BoundSql boundSql = sqlManager.generateQueryForListSql(entityType, null, null);
        return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams());
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
        return jdbcTemplate.queryForList(boundSql.getSql(), boundSql.getParams(), entityType);
    }


    public <E extends IdEntity> Map save(E entity) {
        BoundSql boundSql = entityManager.generateSaveSql(entity, new Ctx());
        log.info(boundSql.toString());
        jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
        SaveCommand command = (SaveCommand) boundSql.getCommand();
        return command.getValueMap();
    }


    /**
     * 全量查询，特殊的查询条件
     *
     * @param entityType
     * @param filterGroup
     * @param orderBy
     * @param <T>
     * @return
     */
    public <T> List<T> queryList(Class<T> entityType, FilterGroup filterGroup, String orderBy) {
        if (defaultFilterOption && defaultFilterGroup != null) {
            for (FilterGroup.Filter filter : defaultFilterGroup.getFilters()) {
                filterGroup.addFilter(filter);
            }
        }
        BoundSql boundSql = sqlManager.generateQueryForObjectOrMapSql(entityType, filterGroup, orderBy);
        log.info(boundSql.toString());
        return jdbcTemplate.query(boundSql.getSql(),  new CommonRowMapper<T>(),boundSql.getParams());
    }

    /**
     * 常用全量查询，全等式查询
     *
     * @param entityType
     * @param params
     * @param orderBy
     * @param <T>
     * @return
     */
    public <T> List<T> queryList(Class<T> entityType, Map<String, Object> params, String orderBy) {
        FilterGroup filterGroup= generateFilterGroup(params);
        return queryList(entityType, filterGroup, orderBy);
    }

    /**
     * 分页查询，特殊条件查询
     *
     * @param entityType
     * @param filterGroup
     * @param request
     * @param <T>
     * @return
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
        BoundSql boundSql = sqlManager.generatePageQuerySql(command, entityType, true, filterGroup, null);
        log.info(boundSql.toString());
        return jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), new CommonRowMapper<T>());
    }

    /**
     * 分页查询，全量查询
     *
     * @param entityType
     * @param params
     * @param request
     * @param <T>
     * @return
     */
    public <T> List<T> pageQueryList(Class<T> entityType, Map<String, Object> params, PageQueryRequest request) {
        FilterGroup filterGroup = generateFilterGroup(params);
        return pageQueryList(entityType, filterGroup, request);
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

    @MethodLog(type = "queryListByView")
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
        log.info(boundSql.toString());
        return jdbcTemplate.queryForList(boundSql.getSql());
    }

    public int delete(Class entityType, String fieldName, Object value) {
        FilterGroup filterGroup = new FilterGroup().    addFilter(fieldName, value.toString());
        BoundSql boundSql = sqlManager.generateDeleteSql(entityType, filterGroup);
        log.info(boundSql.toString());
        return jdbcTemplate.update(boundSql.getSql(), boundSql.getParams());
    }
}
