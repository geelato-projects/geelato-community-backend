package cn.geelato.orm.query;

import cn.geelato.core.sql.SqlManager;
import cn.geelato.orm.adapter.SaveCommandAdapter;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 元数据更新构建器
 * 提供流式API构建SQL更新语句
 */
@Getter
public class MetaUpdate extends MetaOperate<MetaUpdate> {
    private final Map<String, Object> valueMap = new LinkedHashMap<>();
    private final List<MetaInsert> children = new ArrayList<>();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private String[] columns;

    public MetaUpdate(String entityName) {
        this.entityName = entityName;
    }

    public MetaUpdate(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 设置更新的字段
     * @param columns 字段数组
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate column(String[] columns) {
        this.columns = columns;
        return this;
    }

    /**
     * 设置更新的值
     * @param values 值数组
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate values(Object[] values) {
        if (columns == null || columns.length != values.length) {
            throw new IllegalArgumentException("column 与 values 数量不匹配。");
        }
        for (int i = 0; i < columns.length; i++) {
            this.valueMap.put(columns[i], values[i]);
        }
        return this;
    }

    public MetaUpdate value(String field, Object value) {
        this.valueMap.put(field, value);
        return this;
    }

    public MetaUpdate values(Map<String, Object> values) {
        if (values != null) {
            this.valueMap.putAll(values);
        }
        return this;
    }

    public MetaUpdate where(Filter filter) {
        this.filters.add(filter);
        return this;
    }

    public MetaUpdate where(Filter... filters) {
        this.filters.addAll(java.util.Arrays.asList(filters));
        return this;
    }

    public MetaUpdate child(String entity, Consumer<MetaInsert> consumer) {
        MetaInsert child = new MetaInsert(entity);
        consumer.accept(child);
        this.children.add(child);
        return this;
    }

    public String toSql() {
        return sqlManager.generateSaveSql(SaveCommandAdapter.fromUpdate(this)).getSql();
    }

    public String save() {
        return executor().save(SaveCommandAdapter.fromUpdate(this), getConnectId());
    }

    public String execute() {
        return save();
    }
}
