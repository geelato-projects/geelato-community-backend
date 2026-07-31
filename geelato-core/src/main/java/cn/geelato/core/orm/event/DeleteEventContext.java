package cn.geelato.core.orm.event;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

    // ===== A1: 执行结果回传（仅新增字段，老监听器无感） =====
    /** SQL 是否执行成功（在 fireAfter 触发前置 true；失败时为 false）。 */
    private boolean success;
    /** 失败时的异常（Dao 在 catch 中填充）。 */
    private Throwable exception;

    // ===== A2: 事务感知回调容器 =====
    private final List<Runnable> commitCallbacks = new ArrayList<>();
    private final List<Runnable> rollbackCallbacks = new ArrayList<>();

    public DeleteEventContext(Dao dao, SessionCtx sessionCtx, BoundSql boundSql, DeleteCommand command) {
        this.dao = dao;
        this.sessionCtx = sessionCtx;
        this.boundSql = boundSql;
        this.command = command;
        this.eventId = UUID.randomUUID().toString();
        this.startTime = System.currentTimeMillis();
    }

    // ===== A2: 事务感知回调注册入口 =====
    public void onCommit(Runnable callback) {
        if (callback != null) {
            commitCallbacks.add(callback);
        }
    }

    public void onRollback(Runnable callback) {
        if (callback != null) {
            rollbackCallbacks.add(callback);
        }
    }
}
