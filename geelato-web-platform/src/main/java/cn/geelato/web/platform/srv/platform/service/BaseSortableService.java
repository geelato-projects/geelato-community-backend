package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class BaseSortableService extends BaseService {
    private static final String DEFAULT_ORDER_BY = "seq_no ASC";

    /**
     * 全量查询
     * <p>
     * 根据提供的实体类、条件参数和排序规则，执行全量查询操作，并返回查询结果列表。
     *
     * @param entity 查询的实体类
     * @param params 查询条件参数
     * @param <T>    泛型参数，表示查询结果的数据类型
     * @return 返回查询结果列表，列表中的元素类型为指定的实体类类型
     */
    @Override
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params, String orderBy) {
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseSortableService.DEFAULT_ORDER_BY;
        return super.queryModel(entity, params, orderBy);
    }

    /**
     * 全量查询，默认排序
     * <p>
     * 根据提供的实体类、查询参数和默认排序规则执行全量查询，并返回查询结果列表。
     *
     * @param entity 查询的目标实体类
     * @param params 查询参数，包含查询条件等
     * @param <T>    泛型参数，表示查询结果的数据类型
     * @return 返回查询结果的列表，列表中的元素类型为指定的实体类类型
     */
    @Override
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
        return queryModel(entity, params, BaseSortableService.DEFAULT_ORDER_BY);
    }

    /**
     * 全量查询
     * <p>
     * 根据给定的实体类、过滤条件和排序规则，执行全量查询操作。
     *
     * @param entity  查询的实体类类型
     * @param filter  查询条件参数，用于筛选满足条件的实体记录
     * @param orderBy 排序字段和排序方式，如果不指定，则使用默认排序方式
     * @param <T>     泛型参数，表示查询结果的数据类型
     * @return 返回查询结果列表，列表中的元素类型为指定的实体类类型
     */
    @Override
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter, String orderBy) {
        orderBy = Strings.isNotBlank(orderBy) ? orderBy : BaseSortableService.DEFAULT_ORDER_BY;
        return super.queryModel(entity, filter, orderBy);
    }

    /**
     * 重写查询模型方法
     * <p>
     * 使用默认的排序规则对指定实体和过滤条件进行全量查询。
     *
     * @param entity 要查询的实体类
     * @param filter 查询条件组
     * @param <T>    泛型参数，表示查询结果的类型
     * @return 返回查询结果的列表
     */
    @Override
    public <T> List<T> queryModel(Class<T> entity, FilterGroup filter) {
        return queryModel(entity, filter, BaseSortableService.DEFAULT_ORDER_BY);
    }

    /**
     * 创建一条数据
     * <p>
     * 该方法用于在数据库中创建一条新的数据记录。如果传入的实体数据的序列号大于0，则使用该序列号；否则，使用默认序列号。
     *
     * @param model 要创建的实体数据对象，必须继承自BaseSortableEntity类
     * @param <T>   泛型类型，表示实体数据的类型，必须继承自BaseSortableEntity类
     * @return 返回创建后的实体数据对象
     */
    public <T extends BaseSortableEntity> T createModel(T model) {
        model.setSeqNo(model.getSeqNo() > 0 ? model.getSeqNo() : ColumnDefault.SEQ_NO_VALUE);
        return super.createModel(model);
    }

    /**
     * 更新一条数据
     * <p>
     * 该方法用于更新指定实体数据，调用了父类的updateModel方法进行处理。
     *
     * @param model 实体数据对象，需要继承自BaseSortableEntity
     * @param <T>   实体数据的泛型类型
     * @return 更新后的实体数据对象
     */
    public <T extends BaseSortableEntity> T updateModel(T model) {
        return super.updateModel(model);
    }

    /**
     * 逻辑删除
     * <p>
     * 将指定的模型对象进行逻辑删除操作。
     *
     * @param model 要进行逻辑删除的模型对象，该对象必须继承自BaseSortableEntity类
     * @param <T>   泛型类型，表示模型对象的类型，必须继承自BaseSortableEntity
     */
    public <T extends BaseSortableEntity> void isDeleteModel(T model) {
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        super.isDeleteModel(model);
    }
}
