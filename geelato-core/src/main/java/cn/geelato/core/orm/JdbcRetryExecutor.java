package cn.geelato.core.orm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * JDBC 连接获取失败的透明重试执行器，供 {@link Dao} 及其族类在包装 {@link SqlExecuteException} 之前调用。
 * <p>
 * 目标：数据库短暂重启、网络抖动导致连接池短暂借不出连接时，无事务的请求自动重试成功，前端无感知。
 * 覆盖 primary 与动态数据源共用的 {@link Dao} 路径，与连接池 keepalive/借出校验配套。
 * </p>
 *
 * <h3>重试判定（绝对安全子集）</h3>
 * <p>仅当 {@link CannotGetJdbcConnectionException}——从连接池<b>获取连接失败</b>，SQL 必然未发送到数据库，
 * 重新执行不可能产生任何副作用（不会重复写入、不会干扰事务内的连接绑定与多数据源路由）。
 * 连接池参数（connectionTimeout）内 Hikari 已自行多次尝试，仍失败才抛出该异常，本类再补两个重试窗口。</p>
 *
 * <h3>不重试的场景</h3>
 * <ul>
 *   <li>当前线程存在活动事务（{@link TransactionSynchronizationManager#isActualTransactionActive()}）：
 *       事务内的连接绑定与多数据源路由语义（如 batchSave 按目标库开事务、事务内切换数据源）
 *       不允许在 Dao 层擅自重新获取连接；</li>
 *   <li>执行中途的连接断开（TransientDataAccessException、sqlState 08xxx、Communications link failure 等）：
 *       SQL 可能已发送甚至已提交，重试有重复执行风险——这类故障由异常分类（{@link SqlExecuteException#of}，
 *       10021 等）转为友好提示，交由用户重试；</li>
 *   <li>非连接类故障（语法错误、约束冲突、权限等）：重试无意义。</li>
 * </ul>
 *
 * <h3>重试策略（固化）</h3>
 * <p>首次失败后重试 {@value #DEFAULT_MAX_ATTEMPTS} 次，退避 {@code 300ms/800ms}。
 * 策略与连接池 keepalive 配套、属平台必然行为，不设外部开关；确需调整时改本类的常量。</p>
 */
@Slf4j
public final class JdbcRetryExecutor {

    /** 首次失败后的重试次数。 */
    static final int DEFAULT_MAX_ATTEMPTS = 2;
    /** 各次重试前的退避毫秒序列，最后一次之后的重试沿用最后一个值。 */
    static final long[] DEFAULT_BACKOFF = {300L, 800L};
    private static final int REASON_MAX_LENGTH = 120;

    private JdbcRetryExecutor() {
    }

    /**
     * 执行 action，仅当"未从连接池获得连接"（CannotGetJdbcConnectionException）且无活动事务时透明重试；
     * 重试耗尽或故障不可重试时抛出最后一次的 {@link DataAccessException}，由调用方包装为 {@link SqlExecuteException}。
     */
    public static <T> T execute(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return action.get();
        }
        DataAccessException last = null;
        for (int attempt = 0; attempt <= DEFAULT_MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                long waitMillis = DEFAULT_BACKOFF[Math.min(attempt - 1, DEFAULT_BACKOFF.length - 1)];
                log.warn("JDBC获取连接失败，进行第 {}/{} 次重试（退避 {}ms），原因：{}",
                        attempt, DEFAULT_MAX_ATTEMPTS, waitMillis, summarize(last));
                if (!sleep(waitMillis)) {
                    throw last;
                }
            }
            try {
                return action.get();
            } catch (DataAccessException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    /**
     * @see #execute(Supplier)
     */
    public static void executeVoid(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 判断是否属于可安全重试的故障：仅"未从连接池获得连接"（SQL 必然未执行）。
     */
    public static boolean isRetryable(DataAccessException e) {
        return e instanceof CannotGetJdbcConnectionException;
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String summarize(DataAccessException e) {
        String message = e.getMessage();
        if (message != null && message.length() > REASON_MAX_LENGTH) {
            message = message.substring(0, REASON_MAX_LENGTH) + "...";
        }
        return e.getClass().getSimpleName() + ": " + message;
    }
}
