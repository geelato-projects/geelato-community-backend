package cn.geelato.orm.spi;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.command.CommandType;

import java.util.Map;

public class FluentSaveFieldValueFillContext {
    private final String entityName;
    private final CommandType commandType;
    private final EntityMeta entityMeta;
    private final Map<String, Object> defaultEntityMap;
    private final Map<String, Object> targetValueMap;

    public FluentSaveFieldValueFillContext(String entityName, CommandType commandType, EntityMeta entityMeta,
                                           Map<String, Object> defaultEntityMap, Map<String, Object> targetValueMap) {
        this.entityName = entityName;
        this.commandType = commandType;
        this.entityMeta = entityMeta;
        this.defaultEntityMap = defaultEntityMap;
        this.targetValueMap = targetValueMap;
    }

    public String getEntityName() {
        return entityName;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public EntityMeta getEntityMeta() {
        return entityMeta;
    }

    public Map<String, Object> getDefaultEntityMap() {
        return defaultEntityMap;
    }

    public Map<String, Object> getTargetValueMap() {
        return targetValueMap;
    }
}
