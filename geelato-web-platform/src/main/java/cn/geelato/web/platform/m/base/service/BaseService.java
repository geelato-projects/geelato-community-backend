package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.Ctx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.m.security.entity.DataItems;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@Service
public class BaseService {
    public static final String COMPARE_RESULT_ADD = "add";
    public static final String COMPARE_RESULT_UPDATE = "update";
    public static final String COMPARE_RESULT_DELETE = "delete";
    private static final String DEFAULT_ORDER_BY = "update_at DESC";
    @Autowired
    @Qualifier("primaryDao")
    public Dao dao;

    public FilterGroup filterGroup = new FilterGroup().addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));

    /**
     * 分页查询
     *
     * @param params
     * @param request
     * @return
     */
    public ApiPagedResult pageQueryModel(String sqlId, Map<String, Object> params, PageQueryRequest request) {
        ApiPagedResult result = new ApiPagedResult();
        // dao查询
        params.put("orderBy", request.getOrderBy());
        params.put("pageSize", null);
        List<Map<String, Object>> queryList = dao.queryForMapList(sqlId, params);
        params.put("pageSize", request.getPageSize());
        params.put("startNum", request.getPageSize() * (request.getPageNum() - 1));
        List<Map<String, Object>> pageQueryList = dao.queryForMapList(sqlId, params);
        // 分页结果
        result.setPage(request.getPageNum());
        result.setSize(request.getPageSize());
        result.setTotal(queryList != null ? queryList.size() : 0);
        result.setDataSize(pageQueryList != null ? pageQueryList.size() : 0);
        result.setData(new DataItems(pageQueryList, result.getTotal()));

        return result;
    }

    /**
     * 分页查询
     *
     * @param entity
     * @param params
     * @param request
     * @param <T>
     * @return
     */
    public <T> ApiPagedResult pageQueryModel(Class<T> entity, Map<String, Object> params, PageQueryRequest request) {
        ApiPagedResult result = new ApiPagedResult();
        // 配置参数
        dao.setDefaultFilter(true, filterGroup);
        String orderBy = Strings.isNotBlank(request.getOrderBy()) ? request.getOrderBy() : BaseService.DEFAULT_ORDER_BY;
        request.setOrderBy(orderBy);
        // dao查询
        List<T> pageQueryList = dao.pageQueryList(entity, params, request);
        List<T> queryList = dao.queryList(entity, params, orderBy);
        // 分页结果
        result.setPage(request.getPageNum());
        result.setSize(request.getPageSize());
        result.setTotal(queryList != null ? queryList.size() : 0);
        result.setDataSize(pageQueryList != null ? pageQueryList.size() : 0);
        result.setData(new DataItems(pageQueryList, result.getTotal()));

        return result;
    }

    /**
     * 分页查询
     *
     * @param entity
     * @param filter
     * @param request
     * @param <T>
     * @return
     */
    public <T> ApiPagedResult pageQueryModel(Class<T> entity, FilterGroup filter, PageQueryRequest request) {
        ApiPagedResult result = new ApiPagedResult();
        // 配置参数
        dao.setDefaultFilter(true, filterGroup);
        String orderBy = Strings.isNotBlank(request.getOrderBy()) ? request.getOrderBy() : BaseService.DEFAULT_ORDER_BY;
        request.setOrderBy(orderBy);
        // dao查询
        List<T> pageQueryList = dao.pageQueryList(entity, filter, request);
        List<T> queryList = dao.queryList(entity, filter, orderBy);
        // 分页结果
        result.setPage(request.getPageNum());
        result.setSize(request.getPageSize());
        result.setTotal(queryList != null ? queryList.size() : 0);
        result.setDataSize(pageQueryList != null ? pageQueryList.size() : 0);
        result.setData(new DataItems(pageQueryList, result.getTotal()));

        return result;
    }

    /**
     * 全量查询
     *
     * @param entity 查询实体
     * @param params 条件参数
     */
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params, String orderBy) {
        dao.setDefaultFilter(true, filterGroup);
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseService.DEFAULT_ORDER_BY;
        return dao.queryList(entity, params, orderBy);
    }

    /**
     * 全量查询，默认排序
     *
     * @param entity
     * @param params
     * @param <T>
     * @return
     */
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
        return queryModel(entity, params, BaseService.DEFAULT_ORDER_BY);
    }

    /**
     * 全量查询
     *
     * @param entity 查询实体
     * @param filter 条件参数
     */
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter, String orderBy) {
        dao.setDefaultFilter(true, filterGroup);
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseService.DEFAULT_ORDER_BY;
        return dao.queryList(entity, filter, orderBy);
    }

    /**
     * 全量查询，默认排序
     *
     * @param entity
     * @param filter
     * @param <T>
     * @return
     */
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter) {
        dao.setDefaultFilter(true, filterGroup);
        return queryModel(entity, filter, BaseService.DEFAULT_ORDER_BY);
    }

    /**
     * 单条数据获取
     *
     * @param entity 查询实体
     * @param id     实体id
     */
    public <T> T getModel(Class<T> entity, String id) {
        return dao.queryForObject(entity, id);
    }

    /**
     * 创建一条数据
     *
     * @param model 实体数据
     * @param <T>
     * @return
     */
    public <T extends BaseEntity> T createModel(T model) {
        model.setDelStatus(DeleteStatusEnum.NO.getCode());
        model.setDeleteAt(null);
        if (Strings.isBlank(model.getTenantCode())) {
            model.setTenantCode(getSessionTenantCode());
        }

        Map<String, Object> map = dao.save(model);
        return (T) JSON.parseObject(JSON.toJSONString(map), model.getClass());
    }

    /**
     * 更新一条数据
     *
     * @param model 实体数据
     * @param <T>
     * @return
     */
    public <T extends BaseEntity> T updateModel(T model) {
        model.setDelStatus(DeleteStatusEnum.NO.getCode());
        model.setDeleteAt(null);
        if (Strings.isBlank(model.getTenantCode())) {
            model.setTenantCode(getSessionTenantCode());
        }

        Map<String, Object> map = dao.save(model);
        return (T) JSON.parseObject(JSON.toJSONString(map), model.getClass());
    }

    /**
     * 逻辑删除
     *
     * @param model
     * @param <T>
     */
    public <T extends BaseEntity> void isDeleteModel(T model) {
        model.setDelStatus(DeleteStatusEnum.IS.getCode());
        model.setDeleteAt(new Date());
        dao.save(model);
    }

    /**
     * 删除一条数据
     *
     * @param entity 实体
     * @param id     实体id
     */
    public void deleteModel(Class entity, String id) {
        dao.delete(entity, "id", id);
    }

    /**
     * 是否存在数据
     *
     * @param entity 查询实体
     * @param id     实体id
     * @return
     */
    public boolean isExist(Class entity, String id) {
        if (Strings.isNotBlank(id)) {
            return dao.queryForObject(entity, id) != null;
        }
        return false;
    }

    /**
     * 是否存在数据
     *
     * @param entity     查询实体
     * @param fieldName  关联实体字段名称
     * @param fieldValue 关联实体字段值
     */
    public <T> boolean isExist(Class<T> entity, String fieldName, Object fieldValue) {
        if (fieldValue != null) {
            Map<String, Object> params = new HashMap<>();
            params.put(fieldName, fieldValue);
            List<T> userList = this.queryModel(entity, params);
            return userList != null && !userList.isEmpty();
        }

        return false;
    }


    public boolean validate(String tableName, String id, Map<String, String> params) {
        Map<String, Object> map = new HashMap<>();
        // 租户编码
        if (Strings.isBlank(params.get("tenant_code"))) {
            params.put("tenant_code", getSessionTenantCode());
        }
        // 查询表格
        if (Strings.isBlank(tableName)) {
            return false;
        }
        map.put("tableName", tableName);
        // 排除本身
        map.put("id", Strings.isNotBlank(id) ? id : null);
        // 条件限制
        map.put("condition", formatParameter(params));

        List<Map<String, Object>> vlist = dao.queryForMapList("platform_validate", map);
        return vlist.isEmpty();
    }

    public boolean validate(String tableName, String id, Map<String, String> params, Map<String, String> lowers) {
        Map<String, Object> map = new HashMap<>();
        // 租户编码
        if (Strings.isBlank(params.get("tenant_code"))) {
            params.put("tenant_code", getSessionTenantCode());
        }
        // 查询表格
        if (Strings.isBlank(tableName)) {
            return false;
        }
        map.put("tableName", tableName);
        // 排除本身
        map.put("id", Strings.isNotBlank(id) ? id : null);
        // 条件限制
        map.put("condition", formatParameter(params));
        map.put("lowers", formatParameter(lowers));

        List<Map<String, Object>> vlist = dao.queryForMapList("platform_validate_lowers", map);
        return vlist.isEmpty();
    }

    private List<JSONObject> formatParameter(Map<String, String> params) {
        List<JSONObject> list = new ArrayList<>();
        for (Map.Entry<String, String> param : params.entrySet()) {
            Map<String, String> jParams = new HashMap<>();
            if (Strings.isNotBlank(param.getKey())) {
                jParams.put("key", param.getKey());
                jParams.put("value", param.getValue());
                list.add(JSONObject.parseObject(JSON.toJSONString(jParams)));
            }
        }

        return list;
    }

    /**
     * @return 当前会话信息
     */
    protected String getSessionTenantCode() {
        return Ctx.getCurrentTenantCode();
    }

    /**
     * 根据ID获取列表
     *
     * @param id
     * @return 列表
     */
    public <T extends BaseEntity> List getModelsById(Class<T> entity, String id) {
        List<T> list = new ArrayList<>();
        if (Strings.isNotBlank(id)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("id", FilterGroup.Operator.in, id);
            list = this.queryModel(entity, filter);
        }

        return list;
    }

    /**
     * 旧集合与新集合对比，返回新增、更新、删除
     *
     * @param sources
     * @param targets
     * @param <T>
     * @return
     */
    public <T extends BaseEntity> Map<String, List<T>> compareBaseEntity(List<T> sources, List<T> targets) {
        Map<String, List<T>> result = new HashMap<>();
        if (sources != null && sources.size() > 0 && targets != null && targets.size() > 0) {
            List<String> sourceIds = sources.stream().map(T::getId).collect(Collectors.toList());
            List<String> targetIds = targets.stream().map(T::getId).collect(Collectors.toList());
            // 删除的，新的没有
            result.put(COMPARE_RESULT_DELETE, sources.stream().filter(apiParam -> !targetIds.contains(apiParam.getId())).collect(Collectors.toList()));
            // 更新的，旧的有的
            result.put(COMPARE_RESULT_UPDATE, targets.stream().filter(apiParam -> sourceIds.contains(apiParam.getId())).collect(Collectors.toList()));
            // 新增的，旧的没有
            result.put(COMPARE_RESULT_ADD, targets.stream().filter(apiParam -> !sourceIds.contains(apiParam.getId())).collect(Collectors.toList()));
        } else if (sources != null && sources.size() > 0) {
            result.put(COMPARE_RESULT_DELETE, sources);
        } else if (targets != null && targets.size() > 0) {
            result.put(COMPARE_RESULT_ADD, targets);
        }

        return result;
    }
}
