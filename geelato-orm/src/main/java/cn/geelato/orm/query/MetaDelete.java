package cn.geelato.orm.query;

import cn.geelato.core.sql.SqlManager;
import cn.geelato.orm.adapter.DeleteCommandAdapter;

import java.util.Arrays;

/**
 * 元数据删除构建器
 * 提供流式API构建SQL删除语句
 */
public class MetaDelete extends MetaOperate<MetaDelete> {
    private final SqlManager sqlManager = SqlManager.singleInstance();

    public MetaDelete(String entityName) {
        this.entityName = entityName;
    }

    public MetaDelete(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 添加过滤条件
     * @param filter 过滤条件
     * @return MetaDelete对象，支持链式调用
     */
    public MetaDelete where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaDelete对象，支持链式调用
     */
    public MetaDelete where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }

    public String toSql() {
        return sqlManager.generateDeleteSql(DeleteCommandAdapter.from(this)).getSql();
    }

    public int delete() {
        return executor().delete(DeleteCommandAdapter.from(this), getConnectId());
    }

    public int execute() {
        return delete();
    }
}
