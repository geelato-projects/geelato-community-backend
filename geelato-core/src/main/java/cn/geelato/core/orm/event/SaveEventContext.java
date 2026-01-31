package cn.geelato.core.orm.event;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SaveEventContext {
    private final Dao dao;
    private final SessionCtx sessionCtx;
    private final IdEntity entity;
    private BoundSql boundSql;
    private SaveCommand command;
    private Map<String, Object> resultValueMap;
    private final String eventId;
    private final long startTime;

    public SaveEventContext(Dao dao, SessionCtx sessionCtx, IdEntity entity, BoundSql boundSql, SaveCommand command) {
        this.dao = dao;
        this.sessionCtx = sessionCtx;
        this.entity = entity;
        this.boundSql = boundSql;
        this.command = command;
        this.eventId = UUID.randomUUID().toString();
        this.startTime = System.currentTimeMillis();
    }
}
