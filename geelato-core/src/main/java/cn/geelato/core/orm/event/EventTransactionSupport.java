package cn.geelato.core.orm.event;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 事件事务回调触发支持（A2）。
 *
 * <p>处理 {@link SaveEventContext} / {@link DeleteEventContext} 上登记的 commit/rollback 回调：
 * <ul>
 *   <li>有 Spring 事务同步时：注册 {@link TransactionSynchronization}，在 {@code afterCommit} 跑 commit 回调、
 *       {@code afterCompletion(rolled_back)} 跑 rollback 回调；正常提交则 afterCompletion 不重复跑 commit。</li>
 *   <li>无事务同步时：SQL 已成功执行即视为已提交，立即同步跑 commit 回调。</li>
 * </ul>
 *
 * <p>由 Dao 在 {@code fireAfter} 之后调用 {@link #trigger(SaveEventContext)} / {@link #trigger(DeleteEventContext)}。
 * 这是 P0-1（after 异步 + 事务边界不可靠）的根因修复：把"提交后才执行"的能力下放到框架层，
 * 让 {@link TransactionalAfterSaveEventListener} 等监听器的回调只在数据真正落库后触发。
 */
public final class EventTransactionSupport {

    private EventTransactionSupport() {
    }

    public static void trigger(SaveEventContext context) {
        trigger(context.getCommitCallbacks(), context.getRollbackCallbacks());
    }

    public static void trigger(DeleteEventContext context) {
        trigger(context.getCommitCallbacks(), context.getRollbackCallbacks());
    }

    private static void trigger(List<Runnable> commitCallbacks, List<Runnable> rollbackCallbacks) {
        if ((commitCallbacks == null || commitCallbacks.isEmpty())
                && (rollbackCallbacks == null || rollbackCallbacks.isEmpty())) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 有事务：注册同步，提交后跑 commit、回滚跑 rollback
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                private boolean committed = false;

                @Override
                public void afterCommit() {
                    committed = true;
                    runAll(commitCallbacks);
                }

                @Override
                public void afterCompletion(int status) {
                    if (!committed) {
                        // 未走 afterCommit 即为回滚（含 STATUS_ROLLED_BACK）
                        runAll(rollbackCallbacks);
                    }
                }
            });
        } else {
            // 无事务：SQL 已成功执行即视为提交
            runAll(commitCallbacks);
        }
    }

    private static void runAll(List<Runnable> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        for (Runnable r : callbacks) {
            try {
                r.run();
            } catch (Exception e) {
                // 单个回调失败不影响其他回调；事务感知回调内部也应自行吞异常，这里做兜底
                org.slf4j.LoggerFactory.getLogger("cn.geelato.core.orm.event")
                        .error("event transaction callback error", e);
            }
        }
    }
}
