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
 * 元数据插入构建器
 * 提供流式API构建SQL插入语句
 */
@Getter
public class MetaInsert extends MetaOperate<MetaInsert> {
    private final Map<String, Object> valueMap = new LinkedHashMap<>();
    private final List<MetaInsert> children = new ArrayList<>();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private String[] columns;

    public MetaInsert(String entityName) {
        this.entityName = entityName;
    }

    public MetaInsert(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 设置插入的字段
     * @param columns 字段数组
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert column(String[] columns) {
        this.columns = columns;
        return this;
    }

    /**
     * 设置插入的值
     * @param values 值数组
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert values(Object[] values) {
        if (columns == null || columns.length != values.length) {
            throw new IllegalArgumentException("column 与 values 数量不匹配。");
        }
        for (int i = 0; i < columns.length; i++) {
            this.valueMap.put(columns[i], values[i]);
        }
        return this;
    }

    public MetaInsert value(String field, Object value) {
        this.valueMap.put(field, value);
        return this;
    }

    public MetaInsert values(Map<String, Object> values) {
        if (values != null) {
            this.valueMap.putAll(values);
        }
        return this;
    }

    public MetaInsert child(String entity, Consumer<MetaInsert> consumer) {
        MetaInsert child = new MetaInsert(entity);
        consumer.accept(child);
        child.setParent(this);
        this.children.add(child);
        return this;
    }

    public List<String> batch(List<Map<String, Object>> payloads) {
        List<cn.geelato.core.mql.command.SaveCommand> commands = new ArrayList<>();
        if (payloads != null) {
            for (Map<String, Object> payload : payloads) {
                MetaInsert insert = new MetaInsert(resolveEntityName()).values(payload).useDataSource(getConnectId());
                commands.add(SaveCommandAdapter.fromInsert(insert));
            }
        }
        return executor().batchSave(commands, true, getConnectId());
    }

    public String toSql() {
        return sqlManager.generateSaveSql(SaveCommandAdapter.fromInsert(this)).getSql();
    }

    public String save() {
        return executor().save(SaveCommandAdapter.fromInsert(this), getConnectId());
    }

    public String execute() {
        return save();
    }

    public void setParent(MetaInsert parent) {
        // 仅用于让链路表达更完整，当前 parent 关系在适配阶段通过递归重建。
    }
}
