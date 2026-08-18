package cn.geelato.core.orm.event;

import cn.geelato.core.mql.command.SaveCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OrmEventOperations} 事件编排模板单元测试。
 * 验证唯一事实来源的编排契约：fireBefore(纳入try) → SQL → 回填 → fireAfter → 事务回调；
 * 以及各失败分支（SQL 失败 / before 监听器失败）的回填与不触发 after 语义。
 */
class OrmEventOperationsTest {

    @AfterEach
    void cleanup() {
        SaveEventManager.clearBefore();
        SaveEventManager.clearAfter();
        DeleteEventManager.clearBefore();
        DeleteEventManager.clearAfter();
        QueryEventManager.clearBefore();
        QueryEventManager.clearAfter();
    }

    // ===== save =====

    @Test
    void save_success_backfillsAndFiresAfter() throws Exception {
        AtomicBoolean actionRan = new AtomicBoolean(false);
        AtomicBoolean beforeRanBeforeAction = new AtomicBoolean(false);
        CountDownLatch afterLatch = new CountDownLatch(1);
        SaveCommand command = new SaveCommand();
        command.setValueMap(Map.of("id", "p1"));
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, command);

        SaveEventManager.registerBefore(new BeforeSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
                beforeRanBeforeAction.set(!actionRan.get());
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
        });
        SaveEventManager.registerAfter(new AfterSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
            }

            @Override
            public void afterSave(SaveEventContext context) {
                afterLatch.countDown();
            }

            @Override
            public boolean enabled(SaveEventContext c) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext c) {
                return true;
            }
        });

        OrmEventOperations.save(ctx, () -> actionRan.set(true));

        assertTrue(actionRan.get());
        assertTrue(beforeRanBeforeAction.get(), "before 监听器应先于 SQL 动作执行");
        assertTrue(ctx.isSuccess());
        assertEquals(command.getValueMap(), ctx.getResultValueMap());
        assertTrue(afterLatch.await(2, TimeUnit.SECONDS), "after 应被触发");
    }

    @Test
    void save_sqlFails_backfillsRethrowsAndSkipsAfter() throws Exception {
        AtomicInteger afterCalled = new AtomicInteger();
        RuntimeException boom = new IllegalStateException("sql failed");
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        SaveEventManager.registerAfter(afterListener(afterCalled));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> OrmEventOperations.save(ctx, () -> {
                    throw boom;
                }));

        assertSame(boom, thrown);
        assertFalse(ctx.isSuccess());
        assertSame(boom, ctx.getException());
        Thread.sleep(200);
        assertEquals(0, afterCalled.get(), "失败路径不应触发 after");
    }

    @Test
    void save_beforeListenerFails_backfillsAndSkipsAction() throws Exception {
        AtomicBoolean actionRan = new AtomicBoolean(false);
        AtomicInteger afterCalled = new AtomicInteger();
        RuntimeException boom = new IllegalStateException("before listener failed");
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        SaveEventManager.registerBefore(beforeListener(c -> {
            throw boom;
        }));
        SaveEventManager.registerAfter(afterListener(afterCalled));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> OrmEventOperations.save(ctx, () -> actionRan.set(true)));

        assertSame(boom, thrown);
        assertFalse(actionRan.get(), "before 失败后不应执行 SQL 动作");
        assertFalse(ctx.isSuccess());
        assertSame(boom, ctx.getException());
        Thread.sleep(200);
        assertEquals(0, afterCalled.get(), "before 失败不应触发 after");
    }

    @Test
    void save_transactionalListener_commitCallbackRunsWhenNoTx() throws Exception {
        CountDownLatch commitLatch = new CountDownLatch(1);
        SaveEventContext ctx = new SaveEventContext(null, null, null, null, null);
        SaveEventManager.registerAfter(new TransactionalAfterSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
            }

            @Override
            public void afterSave(SaveEventContext context) {
            }

            @Override
            public void afterCommit(SaveEventContext context) {
                commitLatch.countDown();
            }

            @Override
            public void afterRollback(SaveEventContext context) {
            }

            @Override
            public boolean enabled(SaveEventContext c) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext c) {
                return true;
            }
        });

        OrmEventOperations.save(ctx, () -> {
        });

        // 无事务同步：SQL 成功即视为提交，afterCommit 应同步立即执行
        assertEquals(0, commitLatch.getCount(), "无事务时 commit 回调应立即执行");
    }

    // ===== batchSave =====

    @Test
    void batchSave_success_allContextsBackfilled() throws Exception {
        CountDownLatch afterLatch = new CountDownLatch(2);
        SaveCommand c1 = new SaveCommand();
        c1.setValueMap(Map.of("id", "p1"));
        SaveCommand c2 = new SaveCommand();
        c2.setValueMap(Map.of("id", "p2"));
        SaveEventContext ctx1 = new SaveEventContext(null, null, null, null, c1);
        SaveEventContext ctx2 = new SaveEventContext(null, null, null, null, c2);
        AtomicInteger beforeCount = new AtomicInteger();
        SaveEventManager.registerBefore(beforeListener(c -> beforeCount.incrementAndGet()));
        SaveEventManager.registerAfter(afterListenerWithLatch(afterLatch));

        OrmEventOperations.batchSave(Arrays.asList(ctx1, ctx2), () -> {
        });

        assertEquals(2, beforeCount.get(), "每个上下文都应触发 before");
        assertTrue(ctx1.isSuccess());
        assertTrue(ctx2.isSuccess());
        assertEquals(c1.getValueMap(), ctx1.getResultValueMap());
        assertEquals(c2.getValueMap(), ctx2.getResultValueMap());
        assertTrue(afterLatch.await(2, TimeUnit.SECONDS), "每个上下文都应触发 after");
    }

    @Test
    void batchSave_fails_allContextsBackfilledNoAfter() throws Exception {
        AtomicInteger afterCalled = new AtomicInteger();
        SaveEventContext ctx1 = new SaveEventContext(null, null, null, null, null);
        SaveEventContext ctx2 = new SaveEventContext(null, null, null, null, null);
        RuntimeException boom = new IllegalStateException("batch failed");
        SaveEventManager.registerAfter(afterListener(afterCalled));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> OrmEventOperations.batchSave(Arrays.asList(ctx1, ctx2), () -> {
                    throw boom;
                }));

        assertSame(boom, thrown);
        assertFalse(ctx1.isSuccess());
        assertFalse(ctx2.isSuccess());
        assertSame(boom, ctx1.getException());
        assertSame(boom, ctx2.getException());
        Thread.sleep(200);
        assertEquals(0, afterCalled.get(), "失败路径不应触发 after");
    }

    // ===== delete =====

    @Test
    void delete_success_returnsAffectedRowsAndBackfills() throws Exception {
        CountDownLatch afterLatch = new CountDownLatch(1);
        DeleteEventContext ctx = new DeleteEventContext(null, null, null, null);
        DeleteEventManager.registerAfter(new AfterDeleteEventListener() {
            @Override
            public void beforeDelete(DeleteEventContext context) {
            }

            @Override
            public void afterDelete(DeleteEventContext context) {
                afterLatch.countDown();
            }

            @Override
            public boolean enabled(DeleteEventContext c) {
                return true;
            }

            @Override
            public boolean supports(DeleteEventContext c) {
                return true;
            }
        });

        int rows = OrmEventOperations.delete(ctx, () -> 3);

        assertEquals(3, rows);
        assertTrue(ctx.isSuccess());
        assertEquals(3, ctx.getAffectedRows());
        assertTrue(afterLatch.await(2, TimeUnit.SECONDS), "after 应被触发");
    }

    @Test
    void delete_fails_backfillsRethrows() throws Exception {
        AtomicInteger afterCalled = new AtomicInteger();
        DeleteEventContext ctx = new DeleteEventContext(null, null, null, null);
        RuntimeException boom = new IllegalStateException("delete failed");
        DeleteEventManager.registerAfter(new AfterDeleteEventListener() {
            @Override
            public void beforeDelete(DeleteEventContext context) {
            }

            @Override
            public void afterDelete(DeleteEventContext context) {
                afterCalled.incrementAndGet();
            }

            @Override
            public boolean enabled(DeleteEventContext c) {
                return true;
            }

            @Override
            public boolean supports(DeleteEventContext c) {
                return true;
            }
        });

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> OrmEventOperations.delete(ctx, () -> {
                    throw boom;
                }));

        assertSame(boom, thrown);
        assertFalse(ctx.isSuccess());
        assertSame(boom, ctx.getException());
        Thread.sleep(200);
        assertEquals(0, afterCalled.get(), "失败路径不应触发 after");
    }

    // ===== query =====

    @Test
    void query_success_rowCountAndResult() throws Exception {
        CountDownLatch afterLatch = new CountDownLatch(1);
        QueryEventContext ctx = new QueryEventContext(null, null, null, null, Object.class);
        QueryEventManager.registerAfter(afterQueryListener(afterLatch));

        List<String> result = OrmEventOperations.queryList(ctx, () -> Arrays.asList("a", "b"));

        assertEquals(Arrays.asList("a", "b"), result);
        assertTrue(ctx.isSuccess());
        assertEquals(2, ctx.getRowCount());
        assertTrue(afterLatch.await(2, TimeUnit.SECONDS), "after 应被触发");
    }

    @Test
    void query_fails_backfillsAndStillFiresAfter() throws Exception {
        CountDownLatch afterLatch = new CountDownLatch(1);
        QueryEventContext ctx = new QueryEventContext(null, null, null, null, Object.class);
        QueryEventManager.registerAfter(afterQueryListener(afterLatch));
        RuntimeException boom = new IllegalStateException("query failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> OrmEventOperations.queryList(ctx, () -> {
                    throw boom;
                }));

        assertSame(boom, thrown);
        assertFalse(ctx.isSuccess());
        assertSame(boom, ctx.getException());
        assertEquals(0, ctx.getRowCount());
        // 查询失败也要触发 after（供失败率/慢查询统计），与写操作的失败语义不同
        assertTrue(afterLatch.await(2, TimeUnit.SECONDS), "查询失败也应触发 after");
    }

    // ===== 辅助 =====

    private BeforeSaveEventListener beforeListener(java.util.function.Consumer<SaveEventContext> action) {
        return new BeforeSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
                action.accept(context);
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
        };
    }

    private AfterSaveEventListener afterListener(AtomicInteger counter) {
        return new AfterSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
            }

            @Override
            public void afterSave(SaveEventContext context) {
                counter.incrementAndGet();
            }

            @Override
            public boolean enabled(SaveEventContext c) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext c) {
                return true;
            }
        };
    }

    private AfterSaveEventListener afterListenerWithLatch(CountDownLatch latch) {
        return new AfterSaveEventListener() {
            @Override
            public void beforeSave(SaveEventContext context) {
            }

            @Override
            public void afterSave(SaveEventContext context) {
                latch.countDown();
            }

            @Override
            public boolean enabled(SaveEventContext c) {
                return true;
            }

            @Override
            public boolean supports(SaveEventContext c) {
                return true;
            }
        };
    }

    private AfterQueryEventListener afterQueryListener(CountDownLatch latch) {
        return new AfterQueryEventListener() {
            @Override
            public void beforeQuery(QueryEventContext context) {
            }

            @Override
            public void afterQuery(QueryEventContext context) {
                latch.countDown();
            }

            @Override
            public boolean enabled(QueryEventContext c) {
                return true;
            }

            @Override
            public boolean supports(QueryEventContext c) {
                return true;
            }
        };
    }
}
