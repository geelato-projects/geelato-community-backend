package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
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
    @Autowired
    @Qualifier("dynamicDao")
    public Dao dynamicDao;

    public FilterGroup filterGroup = new FilterGroup().addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));

    /**
     * 分页查询
     * <p>
     * 该方法用于执行分页查询操作，并返回分页查询结果。
     *
     * @param sqlId   SQL语句的标识符，用于指定要执行的SQL查询语句
     * @param params  SQL查询所需的参数，以键值对的形式提供
     * @param request 分页查询请求对象，包含分页信息（如页码、每页条数等）
     * @return 返回分页查询结果，包括分页信息、数据总数、数据列表等
     */
    public ApiPagedResult pageQueryModel(String sqlId, Map<String, Object> params, PageQueryRequest request) {
        // dao查询
        params.put("orderBy", request.getOrderBy());
        params.put("pageSize", null);
        List<Map<String, Object>> queryList = dao.queryForMapList(sqlId, params);
        params.put("pageSize", request.getPageSize());
        params.put("startNum", request.getPageSize() * (request.getPageNum() - 1));
        List<Map<String, Object>> pageQueryList = dao.queryForMapList(sqlId, params);
        // 分页结果
        long total = queryList != null ? queryList.size() : 0;
        int dataSize = pageQueryList != null ? pageQueryList.size() : 0;
        return ApiPagedResult.success(new DataItems<>(pageQueryList, total), request.getPageNum(), request.getPageSize(), dataSize, total);
    }

    /**
     * 分页查询
     * <p>
     * 该方法用于对指定实体进行分页查询。
     *
     * @param entity  需要查询的实体类类型
     * @param params  查询参数，用于构造查询条件
     * @param request 分页查询请求对象，包含分页参数和排序参数
     * @param <T>     泛型，表示需要查询的实体类型
     * @return 返回包含分页查询结果的ApiPagedResult对象
     */
    public <T> ApiPagedResult pageQueryModel(Class<T> entity, Map<String, Object> params, PageQueryRequest request) {
        // 配置参数
        dao.setDefaultFilter(true, filterGroup);
        String orderBy = Strings.isNotBlank(request.getOrderBy()) ? request.getOrderBy() : BaseService.DEFAULT_ORDER_BY;
        request.setOrderBy(orderBy);
        // dao查询
        return dao.pageQueryResult(entity, params, request);
    }

    /**
     * 分页查询方法
     * <p>
     * 该方法用于对指定实体类进行分页查询，并返回分页结果。
     *
     * @param <T>     实体类类型
     * @param entity  要查询的实体类
     * @param filter  查询条件组
     * @param request 分页查询请求参数
     * @return 返回分页查询结果，包括分页信息、查询结果等
     */
    public <T> ApiPagedResult pageQueryModel(Class<T> entity, FilterGroup filter, PageQueryRequest request) {
        // 配置参数
        dao.setDefaultFilter(true, filterGroup);
        String orderBy = Strings.isNotBlank(request.getOrderBy()) ? request.getOrderBy() : BaseService.DEFAULT_ORDER_BY;
        request.setOrderBy(orderBy);
        return dao.pageQueryResult(entity, filter, request);
    }

    /**
     * 全量查询
     * <p>
     * 根据给定的实体类和条件参数执行全量查询，并返回查询结果列表。
     *
     * @param entity  查询的实体类
     * @param params  查询条件参数，以键值对的形式表示
     * @param orderBy 排序规则，指定查询结果的排序方式
     * @return 返回查询结果列表，列表中的元素类型为指定的实体类类型
     */
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params, String orderBy) {
        dao.setDefaultFilter(true, filterGroup);
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseService.DEFAULT_ORDER_BY;
        return dao.queryList(entity, params, orderBy);
    }

    /**
     * 全量查询，默认排序
     * <p>
     * 执行全量查询操作，并使用默认的排序规则对查询结果进行排序。
     *
     * @param entity 查询的目标实体类
     * @param params 查询参数，包含查询条件等
     * @param <T>    泛型参数，表示查询结果的数据类型
     * @return 返回查询结果的列表，列表中的元素类型为泛型参数T
     */
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
        return queryModel(entity, params, BaseService.DEFAULT_ORDER_BY);
    }

    /**
     * 全量查询
     * <p>
     * 根据给定的实体类和条件参数进行全量查询，并返回查询结果列表。
     *
     * @param entity  查询的实体类
     * @param filter  条件参数，用于过滤查询结果
     * @param orderBy 排序字段和排序方式，如果不指定，则使用默认排序方式
     * @return 返回查询结果列表，列表中的元素类型为指定的实体类类型
     */
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter, String orderBy) {
        dao.setDefaultFilter(true, filterGroup);
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseService.DEFAULT_ORDER_BY;
        return dao.queryList(entity, filter, orderBy);
    }

    /**
     * 全量查询，使用默认排序
     * <p>
     * 根据指定的实体类和过滤条件进行全量查询，并使用默认的排序规则对查询结果进行排序。
     *
     * @param entity 要查询的实体类类型
     * @param filter 查询条件，用于筛选满足条件的实体记录
     * @param <T>    实体类的泛型类型
     * @return 返回查询到的实体记录列表
     */
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter) {
        dao.setDefaultFilter(true, filterGroup);
        return queryModel(entity, filter, BaseService.DEFAULT_ORDER_BY);
    }

    /**
     * 单条数据获取
     * <p>
     * 根据给定的实体类和ID，从数据库中查询并返回对应的实体对象。
     *
     * @param entity 查询的实体类
     * @param id     实体的ID
     * @return 返回查询到的实体对象，如果未找到对应的数据则返回null
     */
    public <T> T getModel(Class<T> entity, String id) {
        return dao.queryForObject(entity, id);
    }

    /**
     * 创建一条数据
     * <p>
     * 该方法用于在数据库中创建一条新的数据记录。
     *
     * @param model 要创建的实体数据对象
     * @param <T>   泛型类型，表示实体数据的类型，必须继承自BaseEntity类
     * @return 返回创建后的实体数据对象
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
     * <p>
     * 更新指定实体数据，包括设置删除状态为未删除、清空删除时间，并处理租户代码。
     *
     * @param model 实体数据对象，需要继承自BaseEntity
     * @param <T>   实体数据的泛型类型
     * @return 更新后的实体数据对象
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
     * 逻辑删除方法
     * <p>
     * 该方法将指定模型标记为已删除状态，但不从数据库中实际删除该记录。
     *
     * @param model 要进行逻辑删除的模型对象
     * @param <T>   模型对象的类型，必须继承自BaseEntity
     */
    public <T extends BaseEntity> void isDeleteModel(T model) {
        model.setDelStatus(DeleteStatusEnum.IS.getCode());
        model.setDeleteAt(new Date());
        dao.save(model);
    }

    /**
     * 删除一条数据
     * <p>
     * 根据给定的实体类和实体ID，从数据库中删除对应的数据记录。
     *
     * @param entity 要删除数据的实体类
     * @param id     要删除数据的实体ID
     */
    public void deleteModel(Class entity, String id) {
        dao.delete(entity, "id", id);
    }

    /**
     * 判断是否存在数据
     * <p>
     * 根据提供的实体类和实体ID，判断数据库中是否存在对应的数据记录。
     *
     * @param entity 查询实体对应的类
     * @param id     要查询的实体ID
     * @return 如果存在对应的数据记录，则返回true；否则返回false
     */
    public boolean isExist(Class entity, String id) {
        if (Strings.isNotBlank(id)) {
            return dao.queryForObject(entity, id) != null;
        }
        return false;
    }

    /**
     * 判断是否存在数据
     * <p>
     * 根据给定的实体类、字段名称和字段值，判断数据库中是否存在满足条件的数据记录。
     *
     * @param entity     要查询的实体类类型
     * @param fieldName  关联实体的字段名称
     * @param fieldValue 关联实体的字段值
     * @return 如果存在满足条件的数据记录，则返回true；否则返回false
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

    /**
     * 验证表格数据的有效性
     * <p>
     * 根据给定的表名、ID和参数，验证表格数据是否有效。
     *
     * @param tableName 要验证的表格名称
     * @param id        要验证的数据ID，如果为空则不限制ID
     * @param params    验证参数，包含验证所需的数据
     * @return 如果验证通过返回true，否则返回false
     */
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

    /**
     * 验证数据
     * <p>
     * 根据提供的表名、ID和参数，执行数据验证操作。
     *
     * @param tableName 表名，表示要验证的数据所在的表
     * @param id        数据ID，用于排除特定记录
     * @param params    验证参数，包含要验证的字段及其值
     * @param lowers    验证参数的低级版本，用于处理某些特殊需求
     * @return 如果验证通过（即没有查询到冲突数据），则返回true；否则返回false
     */
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

    /**
     * 格式化参数
     * <p>
     * 将传入的参数Map转换为JSONObject列表。
     *
     * @param params 参数Map，键为参数名，值为参数值
     * @return 返回格式化后的JSONObject列表，每个JSONObject包含"key"和"value"两个字段
     */
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
     * 获取当前会话的租户编码。
     *
     * @return 返回当前会话的租户编码。
     */
    protected String getSessionTenantCode() {
        return SessionCtx.getCurrentTenantCode();
    }

    /**
     * 根据ID获取列表
     * <p>
     * 根据提供的实体类类型和ID字符串，查询并返回对应的实体对象列表。
     *
     * @param entity 要查询的实体类类型
     * @param id     包含要查询的实体ID的字符串，多个ID之间用逗号分隔
     * @return 返回查询到的实体对象列表，如果未找到对应的数据则返回空列表
     */
    public <T extends BaseEntity> List<T> getModelsById(Class<T> entity, String id) {
        List<T> list = new ArrayList<>();
        if (Strings.isNotBlank(id)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("id", FilterGroup.Operator.in, id);
            list = this.queryModel(entity, filter);
        }

        return list;
    }

    /**
     * 对比旧集合与新集合，返回新增、更新、删除的实体列表
     * <p>
     * 该方法用于比较两个实体集合（旧集合和新集合），并返回新增、更新和删除的实体列表。
     *
     * @param sources 旧集合，包含旧的数据实体列表
     * @param targets 新集合，包含新的数据实体列表
     * @param <T>     泛型类型，表示实体类型，必须继承自BaseEntity类
     * @return 返回一个包含新增、更新、删除实体列表的Map，键为比较结果类型（新增、更新、删除），值为对应类型的实体列表
     */
    public <T extends BaseEntity> Map<String, List<T>> compareBaseEntity(List<T> sources, List<T> targets) {
        Map<String, List<T>> result = new HashMap<>();
        if (sources != null && !sources.isEmpty() && targets != null && !targets.isEmpty()) {
            List<String> sourceIds = sources.stream().map(T::getId).toList();
            List<String> targetIds = targets.stream().map(T::getId).toList();
            // 删除的，新的没有
            result.put(COMPARE_RESULT_DELETE, sources.stream().filter(apiParam -> !targetIds.contains(apiParam.getId())).collect(Collectors.toList()));
            // 更新的，旧的有的
            result.put(COMPARE_RESULT_UPDATE, targets.stream().filter(apiParam -> sourceIds.contains(apiParam.getId())).collect(Collectors.toList()));
            // 新增的，旧的没有
            result.put(COMPARE_RESULT_ADD, targets.stream().filter(apiParam -> !sourceIds.contains(apiParam.getId())).collect(Collectors.toList()));
        } else if (sources != null && !sources.isEmpty()) {
            result.put(COMPARE_RESULT_DELETE, sources);
        } else if (targets != null && !targets.isEmpty()) {
            result.put(COMPARE_RESULT_ADD, targets);
        }

        return result;
    }

    public void switchDbByConnectId(String connectId) {
        if (Strings.isBlank(connectId)) {
            throw new IllegalArgumentException("数据连接不能为空");
        }
        DynamicDatasourceHolder.setDataSourceKey(connectId);
    }
}
