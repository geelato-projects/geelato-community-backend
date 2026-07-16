package cn.geelato.core.mql.spi;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.command.CommandType;

import java.util.Map;

public class MqlSaveFieldValueFillContext {
    private final String entityName;
    private final CommandType commandType;
    private final EntityMeta entityMeta;
    private final Map<String, Object> defaultEntityMap;
    private final Map<String, Object> targetValueMap;
    private final SessionCtx sessionCtx;

    public MqlSaveFieldValueFillContext(String entityName, CommandType commandType, EntityMeta entityMeta,
                                        Map<String, Object> defaultEntityMap, Map<String, Object> targetValueMap,
                                        SessionCtx sessionCtx) {
        this.entityName = entityName;
        this.commandType = commandType;
        this.entityMeta = entityMeta;
        this.defaultEntityMap = defaultEntityMap;
        this.targetValueMap = targetValueMap;
        this.sessionCtx = sessionCtx;
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
}
