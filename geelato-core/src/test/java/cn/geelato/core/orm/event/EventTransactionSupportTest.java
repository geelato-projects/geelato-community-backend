package cn.geelato.core.orm.event;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventTransactionSupport} 测试。
 *
 * <p>无事务场景（{@code isSynchronizationActive=false}）：commit 回调立即执行、rollback 不执行。
 * 有事务场景需 mock TransactionSynchronizationManager（依赖 Spring，本测试覆盖无事务分支）。
 */
class EventTransactionSupportTest {

    @Test
    void trigger_noTransaction_runsCommitImmediately() {
        // 确保无事务同步激活
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());

        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean rolled = new AtomicBoolean();
        ctx.onCommit(() -> committed.set(true));
        ctx.onRollback(() -> rolled.set(true));

        EventTransactionSupport.trigger(ctx);

        // 无事务：commit 立即执行
        assertTrue(committed.get());
        // rollback 不执行（无事务视为已提交）
        assertFalse(rolled.get());
    }

    @Test
    void trigger_emptyCallbacks_isNoop() {
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        assertDoesNotThrow(() -> EventTransactionSupport.trigger(ctx));
    }

    @Test
    void trigger_callbackException_doesNotPropagate() {
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        AtomicBoolean second = new AtomicBoolean();
        // 第一个回调抛异常，不应影响第二个、也不应抛出
        ctx.onCommit(() -> {
            throw new RuntimeException("boom");
        });
        ctx.onCommit(() -> second.set(true));

        assertDoesNotThrow(() -> EventTransactionSupport.trigger(ctx));
        assertTrue(second.get());
    }

    @Test
    void trigger_deleteContext_runsCommitCallbacks() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        DeleteEventContext ctx = new DeleteEventContext(null, null, null, null);
        AtomicBoolean committed = new AtomicBoolean();
        ctx.onCommit(() -> committed.set(true));
        EventTransactionSupport.trigger(ctx);
        assertTrue(committed.get());
    }
}
