package cn.geelato.core.meta.spi;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.mql.command.CommandType;

import java.util.Map;

public class EntitySaveFieldValueFillContext {
    private final String entityName;
    private final CommandType commandType;
    private final EntityMeta entityMeta;
    private final Map<String, Object> defaultEntityMap;
    private final Map<String, Object> targetValueMap;
    private final SessionCtx sessionCtx;
    private final IdEntity entityObject;

    public EntitySaveFieldValueFillContext(String entityName, CommandType commandType, EntityMeta entityMeta,
                                           Map<String, Object> defaultEntityMap, Map<String, Object> targetValueMap,
                                           SessionCtx sessionCtx, IdEntity entityObject) {
        this.entityName = entityName;
        this.commandType = commandType;
        this.entityMeta = entityMeta;
        this.defaultEntityMap = defaultEntityMap;
        this.targetValueMap = targetValueMap;
        this.sessionCtx = sessionCtx;
        this.entityObject = entityObject;
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

    public SessionCtx getSessionCtx() {
        return sessionCtx;
    }

    public IdEntity getEntityObject() {
        return entityObject;
    }
}
