package cn.geelato.core.orm.event;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 查询事件上下文（C2）。
 *
 * <p>结构与 {@link SaveEventContext} 对称，用于查询拦截场景（读审计、慢查询统计、缓存预热、数据权限）。
 * 在 Dao 的查询方法（queryForMapList/queryList 等）执行前后触发。
 */
@Getter
@Setter
public class QueryEventContext {
    private final Dao dao;
    private final SessionCtx sessionCtx;
    private BoundSql boundSql;
    private QueryCommand command;
    /** 实体类型（查询目标），可能为 null（原生 SQL 查询）。 */
    private Class<?> entityType;
    /** 查询是否成功。 */
    private boolean success;
    /** 失败异常。 */
    private Throwable exception;
    /** 查询返回行数（after 阶段填充，便于慢查询/大结果集统计）。 */
    private int rowCount;
    private final String eventId;
    private final long startTime;

    public QueryEventContext(Dao dao, SessionCtx sessionCtx, BoundSql boundSql, QueryCommand command, Class<?> entityType) {
        this.dao = dao;
        this.sessionCtx = sessionCtx;
        this.boundSql = boundSql;
        this.command = command;
        this.entityType = entityType;
        this.eventId = UUID.randomUUID().toString();
        this.startTime = System.currentTimeMillis();
    }
}
