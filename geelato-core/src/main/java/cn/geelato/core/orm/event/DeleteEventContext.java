package cn.geelato.core.orm.event;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DeleteEventContext {
    private final Dao dao;
    private final SessionCtx sessionCtx;
    private BoundSql boundSql;
    private DeleteCommand command;
    private int affectedRows;
    private final String eventId;
    private final long startTime;

    public DeleteEventContext(Dao dao, SessionCtx sessionCtx, BoundSql boundSql, DeleteCommand command) {
        this.dao = dao;
        this.sessionCtx = sessionCtx;
        this.boundSql = boundSql;
        this.command = command;
        this.eventId = UUID.randomUUID().toString();
        this.startTime = System.currentTimeMillis();
    }
}
