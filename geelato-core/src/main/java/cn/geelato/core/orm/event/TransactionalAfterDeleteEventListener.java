package cn.geelato.core.orm.event;

/**
 * 事务感知的删除事件监听器（after 阶段）。
 *
 * <p>语义与 {@link TransactionalAfterSaveEventListener} 对称：{@link #afterCommit} 仅在事务提交后触发，
 * {@link #afterRollback} 在回滚后触发；无事务则 {@link #afterCommit} 立即触发。
 *
 * <p>事务感知由 {@code DeleteEventManager.fireAfter} 检测本接口后，通过
 * {@link DeleteEventContext#onCommit(Runnable)} / {@link DeleteEventContext#onRollback(Runnable)} 登记，
 * 由 Dao 侧结合 Spring {@code TransactionSynchronizationManager} 触发。
 *
 * <p>仍继承 {@link AfterDeleteEventListener}，老 {@code afterDelete} 也会异步触发（兼容）。
 */
public interface TransactionalAfterDeleteEventListener extends AfterDeleteEventListener {

    /** 事务提交后触发。 */
    void afterCommit(DeleteEventContext context);

    /** 事务回滚后触发。 */
    void afterRollback(DeleteEventContext context);
}
