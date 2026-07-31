package cn.geelato.core.orm.event;

/**
 * 事务感知的保存事件监听器（after 阶段）。
 *
 * <p>普通的 {@link AfterSaveEventListener#afterSave(SaveEventContext)} 是异步执行、且在事务提交前调度，
 * 监听器无法保证读到已提交的数据（事务可能尚未 commit 或已回滚）。
 *
 * <p>实现本接口的监听器，其 {@link #afterCommit} 仅在<b>事务真正提交后</b>触发；
 * {@link #afterRollback} 在事务回滚后触发。无事务环境则 {@link #afterCommit} 立即触发（视为已提交）。
 *
 * <p>事务感知由框架在 {@code SaveEventManager.fireAfter} 时统一登记：检测到监听器实现本接口，
 * 则把回调通过 {@link SaveEventContext#onCommit(Runnable)} / {@link SaveEventContext#onRollback(Runnable)}
 * 登记，由 Dao 侧结合 Spring {@code TransactionSynchronizationManager} 在提交/回滚点触发。
 *
 * <p><b>注意</b>：本接口仍继承 {@link AfterSaveEventListener}，老的 {@code afterSave} 也会被异步触发
 * （保持兼容）。若不需要老的异步 after，让 {@code afterSave} 留空即可，只实现 {@code afterCommit/afterRollback}。
 */
public interface TransactionalAfterSaveEventListener extends AfterSaveEventListener {

    /** 事务提交后触发。 */
    void afterCommit(SaveEventContext context);

    /** 事务回滚后触发。 */
    void afterRollback(SaveEventContext context);
}
