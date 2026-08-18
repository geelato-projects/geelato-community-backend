package cn.geelato.core.orm.event;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * ORM 操作的事件编排模板——触发顺序的唯一事实来源。
 *
 * <p>此前同一套触发样板（构造 ctx → fireBefore → 执行 SQL → 结果回填 → fireAfter → 事务回调）
 * 在 {@code Dao} 与 {@code JdbcTemplateMetaExecutionStrategy} 中手写了 20 余遍，且两条路径已出现
 * 行为漂移（success 回填缺失、trigger 缺失、fireBefore 在 try 外导致事务悬挂）。本模板把编排顺序
 * 收敛为单一实现（对标 Hibernate {@code SessionImpl#fire*} 的收敛方式与 Spring
 * {@code TransactionTemplate#execute} 的模板方法），所有执行路径委托至此：
 * <ol>
 *   <li>fireBefore（纳入 try，A3：before 监听器异常与 SQL 异常走同一条回填路径，调用方据此回滚）</li>
 *   <li>执行 SQL 动作</li>
 *   <li>结果回填（A1：success / exception / affectedRows / rowCount）</li>
 *   <li>fireAfter（仅成功路径；after 异步执行，异常仅记日志）</li>
 *   <li>{@link EventTransactionSupport#trigger}（事务感知回调，A2；查询无事务语义除外）</li>
 * </ol>
 *
 * <p>事务边界（begin / commit / rollback）由调用方管理，本模板只负责事件编排与结果回填。
 * 上下文构造也由调用方完成（save 场景需传入实体对象，各路径不同）。
 */
public final class OrmEventOperations {

    private OrmEventOperations() {
    }

    /** 单条保存的 SQL 执行动作。 */
    @FunctionalInterface
    public interface SaveAction {
        void execute();
    }

    /** 单条删除的 SQL 执行动作，返回受影响行数。 */
    @FunctionalInterface
    public interface DeleteAction {
        int execute();
    }

    /** 批量执行动作（一次 batchUpdate 服务多个上下文）。 */
    @FunctionalInterface
    public interface BatchAction {
        void execute();
    }

    /** 查询执行动作，返回结果集。 */
    @FunctionalInterface
    public interface QueryAction<T> {
        T execute();
    }

    /**
     * 单条保存：fireBefore → SQL → 回填 → fireAfter → 事务回调。
     *
     * <p>失败（含 before 监听器异常）回填 {@code success=false/exception} 后原样重抛，不触发 fireAfter 与事务回调。
     * 成功时回填 {@code success=true} 与 {@code resultValueMap}（command 非空时）。
     */
    public static void save(SaveEventContext ctx, SaveAction action) {
        try {
            // A3：fireBefore 纳入 try，before 监听器异常与 SQL 异常统一走回填路径
            SaveEventManager.fireBefore(ctx);
            action.execute();
        } catch (RuntimeException ex) {
            // A1：失败回填
            ctx.setSuccess(false);
            ctx.setException(ex);
            throw ex;
        }
        ctx.setSuccess(true);
        if (ctx.getCommand() != null) {
            ctx.setResultValueMap(ctx.getCommand().getValueMap());
        }
        SaveEventManager.fireAfter(ctx);
        // A2：触发事务感知回调（有事务则提交后执行，无事务立即执行）
        EventTransactionSupport.trigger(ctx);
    }

    /**
     * 批量保存（一次批量 SQL + N 个上下文）：全部 fireBefore → 批量执行 → 逐个回填 + fireAfter + 事务回调。
     *
     * <p>任一环节失败（含任一 before 监听器异常），所有上下文回填失败后原样重抛，
     * 不触发 fireAfter 与事务回调。
     */
    public static void batchSave(List<SaveEventContext> contexts, BatchAction action) {
        try {
            for (SaveEventContext ctx : contexts) {
                SaveEventManager.fireBefore(ctx);
            }
            action.execute();
        } catch (RuntimeException ex) {
            for (SaveEventContext ctx : contexts) {
                ctx.setSuccess(false);
                ctx.setException(ex);
            }
            throw ex;
        }
        for (SaveEventContext ctx : contexts) {
            ctx.setSuccess(true);
            if (ctx.getCommand() != null) {
                ctx.setResultValueMap(ctx.getCommand().getValueMap());
            }
            SaveEventManager.fireAfter(ctx);
            EventTransactionSupport.trigger(ctx);
        }
    }

    /**
     * 单条删除：契约同 {@link #save}，动作返回受影响行数并回填到上下文。
     */
    public static int delete(DeleteEventContext ctx, DeleteAction action) {
        int affectedRows;
        try {
            DeleteEventManager.fireBefore(ctx);
            affectedRows = action.execute();
        } catch (RuntimeException ex) {
            ctx.setSuccess(false);
            ctx.setException(ex);
            throw ex;
        }
        ctx.setSuccess(true);
        ctx.setAffectedRows(affectedRows);
        DeleteEventManager.fireAfter(ctx);
        EventTransactionSupport.trigger(ctx);
        return affectedRows;
    }

    /**
     * 查询：fireBefore → SQL → 回填 rowCount/success → fireAfter。
     *
     * <p>与写操作不同，查询失败也会触发 fireAfter（回填 {@code success=false/exception}），
     * 供慢查询、失败率统计类监听器使用。查询无事务语义，不涉及 {@link EventTransactionSupport}。
     *
     * @param rowCount 从结果集推导行数的函数（如 {@code List::size}）
     */
    public static <T> T query(QueryEventContext ctx, QueryAction<T> action, ToIntFunction<T> rowCount) {
        QueryEventManager.fireBefore(ctx);
        try {
            T result = action.execute();
            ctx.setRowCount(rowCount.applyAsInt(result));
            ctx.setSuccess(true);
            QueryEventManager.fireAfter(ctx);
            return result;
        } catch (RuntimeException ex) {
            ctx.setRowCount(0);
            ctx.setSuccess(false);
            ctx.setException(ex);
            QueryEventManager.fireAfter(ctx);
            throw ex;
        }
    }

    /** List 结果集查询的便捷重载，rowCount 取结果集大小。 */
    public static <T> List<T> queryList(QueryEventContext ctx, QueryAction<List<T>> action) {
        return query(ctx, action, result -> result == null ? 0 : result.size());
    }
}
