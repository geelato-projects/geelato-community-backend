package cn.geelato.core.orm.event;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

    // ===== A1: 执行结果回传（仅新增字段，老监听器无感） =====
    /** SQL 是否执行成功（在 fireAfter 触发前置 true；失败时为 false）。 */
    private boolean success;
    /** 失败时的异常（Dao 在 catch 中填充），供监听器/诊断使用。 */
    private Throwable exception;
    /** 受影响行数（与 DeleteEventContext 对齐）。 */
    private int affectedRows;

    // ===== A2: 事务感知回调容器 =====
    /** 事务提交后执行的回调（仅当外层事务真正提交时触发；无事务时立即执行）。 */
    private final List<Runnable> commitCallbacks = new ArrayList<>();
    /** 事务回滚后执行的回调。 */
    private final List<Runnable> rollbackCallbacks = new ArrayList<>();

    public SaveEventContext(Dao dao, SessionCtx sessionCtx, IdEntity entity, BoundSql boundSql, SaveCommand command) {
        this.dao = dao;
        this.sessionCtx = sessionCtx;
        this.entity = entity;
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

    // ===== C1: insert/update 便捷判断（避免监听器正则解析 SQL） =====
    /** 操作类型（Insert/Update），从 command 提取；command 为空时为 null。 */
    public CommandType getOperType() {
        return command != null ? command.getCommandType() : null;
    }

    /** 是否为新增操作。 */
    public boolean isInsert() {
        return command != null && command.getCommandType() == CommandType.Insert;
    }

    /** 是否为更新操作。 */
    public boolean isUpdate() {
        return command != null && command.getCommandType() == CommandType.Update;
    }
}
