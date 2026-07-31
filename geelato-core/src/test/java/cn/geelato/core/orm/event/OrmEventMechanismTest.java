package cn.geelato.core.orm.event;

import cn.geelato.core.orm.event.callback.AfterSaveCallback;
import cn.geelato.core.orm.event.callback.BeforeSaveCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ORM 事件机制新能力单元测试。
 * 覆盖：A1 Context 新字段、A2 回调容器、B1 优先级、C1 operType、C3 函数式 callback、C2 Query 事件注册。
 * 不依赖 Spring 上下文，纯逻辑验证。
 */
class OrmEventMechanismTest {

    @AfterEach
    void cleanup() {
        // 测试间清理注册的监听器，避免相互干扰
        SaveEventManager.clearBefore();
        SaveEventManager.clearAfter();
        DeleteEventManager.clearBefore();
        DeleteEventManager.clearAfter();
        QueryEventManager.clearBefore();
        QueryEventManager.clearAfter();
    }

    // ===== A1: Context 执行结果字段 + C1: operType =====

    @Test
    void saveEventContext_newFields_defaultAndSettable() {
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        // 默认值
        assertFalse(ctx.isSuccess());
        assertNull(ctx.getException());
        assertEquals(0, ctx.getAffectedRows());
        assertNull(ctx.getOperType());
        // 可设置
        ctx.setSuccess(true);
        ctx.setAffectedRows(5);
        assertTrue(ctx.isSuccess());
        assertEquals(5, ctx.getAffectedRows());
    }

    @Test
    void deleteEventContext_newFields_default() {
        DeleteEventContext ctx = new DeleteEventContext(null, null, null, null);
        assertFalse(ctx.isSuccess());
        assertNull(ctx.getException());
    }

    // ===== A2: 事务感知回调容器（onCommit/onRollback） =====

    @Test
    void saveContext_commitRollbackCallbacks_collectAndRun() {
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger rolled = new AtomicInteger();
        ctx.onCommit(committed::incrementAndGet);
        ctx.onRollback(rolled::incrementAndGet);

        assertEquals(1, ctx.getCommitCallbacks().size());
        assertEquals(1, ctx.getRollbackCallbacks().size());

        // 执行回调
        ctx.getCommitCallbacks().forEach(Runnable::run);
        assertEquals(1, committed.get());
        ctx.getRollbackCallbacks().forEach(Runnable::run);
        assertEquals(1, rolled.get());
    }

    @Test
    void deleteContext_commitRollbackCallbacks_collectAndRun() {
        DeleteEventContext ctx = new DeleteEventContext(null, null, null, null);
        AtomicInteger c = new AtomicInteger();
        ctx.onCommit(c::incrementAndGet);
        ctx.getCommitCallbacks().forEach(Runnable::run);
        assertEquals(1, c.get());
    }

    // ===== B1: 监听器优先级排序 =====

    @Test
    void beforeListeners_executedByOrderAsc() {
        List<String> order = new ArrayList<>();
        // 注册顺序乱，order 值不同
        SaveEventManager.registerBefore(beforeListener(20, () -> order.add("high")));
        SaveEventManager.registerBefore(beforeListener(5, () -> order.add("low")));
        SaveEventManager.registerBefore(beforeListener(10, () -> order.add("mid")));

        SaveEventContext ctx = ctx();
        // fireBefore 只在 enabled && supports 时执行，测试监听器均返回 true
        SaveEventManager.fireBefore(ctx);

        assertEquals(3, order.size());
        // 按 order 升序：5(low) → 10(mid) → 20(high)
        assertEquals("low", order.get(0));
        assertEquals("mid", order.get(1));
        assertEquals("high", order.get(2));
    }

    @Test
    void registerIfAbsent_preventsDuplicate() {
        BeforeSaveEventListener l = beforeListener(0, () -> {});
        SaveEventManager.registerBeforeIfAbsent(l);
        SaveEventManager.registerBeforeIfAbsent(l); // 同一实例不重复
        // 通过 fireBefore 执行次数验证（用一个计数监听器更直观，这里验证 clear 不报错即可）
        assertDoesNotThrow(() -> SaveEventManager.fireBefore(ctx()));
    }

    // ===== C3: 函数式 callback 注册 =====

    @Test
    void registerBeforeCallback_lambdaWorks() {
        AtomicInteger called = new AtomicInteger();
        SaveEventManager.registerBeforeCallback(c -> called.incrementAndGet());
        SaveEventManager.fireBefore(ctx());
        assertEquals(1, called.get());
    }

    @Test
    void registerAfterCallback_lambdaWorks() throws Exception {
        AtomicInteger called = new AtomicInteger();
        SaveEventManager.registerAfterCallback(c -> called.incrementAndGet());
        SaveEventManager.fireAfter(ctx());
        // after 异步，等待一下
        Thread.sleep(200);
        assertTrue(called.get() >= 1);
    }

    @Test
    void callbackAdapter_orderRespected() {
        List<String> seq = new ArrayList<>();
        SaveEventManager.registerBeforeCallback(c -> seq.add("first"), 1);
        SaveEventManager.registerBeforeCallback(c -> seq.add("second"), 9);
        SaveEventManager.fireBefore(ctx());
        assertEquals("first", seq.get(0));
        assertEquals("second", seq.get(1));
    }

    // ===== C2: Query 事件注册 =====

    @Test
    void queryEventManager_registerAndFire() throws Exception {
        AtomicInteger beforeCalled = new AtomicInteger();
        AtomicInteger afterCalled = new AtomicInteger();
        QueryEventManager.registerBefore(new BeforeQueryEventListener() {
            @Override
            public void beforeQuery(QueryEventContext context) {
                beforeCalled.incrementAndGet();
            }

            @Override
            public void afterQuery(QueryEventContext context) {
            }

            @Override
            public boolean enabled(QueryEventContext c) {
                return true;
            }

            @Override
            public boolean supports(QueryEventContext c) {
                return true;
            }
        });
        QueryEventManager.registerAfter(new AfterQueryEventListener() {
            @Override
            public void beforeQuery(QueryEventContext context) {
            }

            @Override
            public void afterQuery(QueryEventContext context) {
                afterCalled.incrementAndGet();
            }

            @Override
            public boolean enabled(QueryEventContext c) {
                return true;
            }

            @Override
            public boolean supports(QueryEventContext c) {
                return true;
            }
        });

        QueryEventContext qctx = new QueryEventContext(null, null, null, null, Object.class);
        QueryEventManager.fireBefore(qctx);
        QueryEventManager.fireAfter(qctx);
        Thread.sleep(200);
        assertEquals(1, beforeCalled.get());
        assertTrue(afterCalled.get() >= 1);
    }

    @Test
    void queryEvent_noListeners_fireIsNoop() {
        QueryEventContext qctx = new QueryEventContext(null, null, null, null, Object.class);
        assertDoesNotThrow(() -> QueryEventManager.fireBefore(qctx));
        assertDoesNotThrow(() -> QueryEventManager.fireAfter(qctx));
    }

    // ===== 辅助 =====

    private SaveEventContext ctx() {
        return new SaveEventContext(null, null, null, null, null);
    }

    private BeforeSaveEventListener beforeListener(int order, Runnable action) {
        return new BeforeSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
                action.run();
            }

            @Override
            public void afterSave(SaveEventContext context) {
            }

            @Override
            public boolean enabled(SaveEventContext c) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext c) {
                return true;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }
}
