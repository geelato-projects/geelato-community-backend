package cn.geelato.orm.fill;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.command.CommandType;

import java.util.Map;

public class SaveDefaultValueContext {
    private final String entityName;
    private final CommandType commandType;
    private final EntityMeta entityMeta;
    private final Map<String, Object> entityDefaults;
    private final Map<String, Object> valueMap;

    public SaveDefaultValueContext(String entityName, CommandType commandType, EntityMeta entityMeta,
                                   Map<String, Object> entityDefaults, Map<String, Object> valueMap) {
        this.entityName = entityName;
        this.commandType = commandType;
        this.entityMeta = entityMeta;
        this.entityDefaults = entityDefaults;
        this.valueMap = valueMap;
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

    public Map<String, Object> getEntityDefaults() {
        return entityDefaults;
    }

    public Map<String, Object> getValueMap() {
        return valueMap;
    }
}
