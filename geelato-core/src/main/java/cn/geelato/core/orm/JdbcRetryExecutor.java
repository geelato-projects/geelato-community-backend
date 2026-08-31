package cn.geelato.core.orm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * JDBC 连接类故障的透明重试执行器，供 {@link Dao} 及其族类在包装 {@link SqlExecuteException} 之前调用。
 * <p>
 * 目标：数据库短暂重启、网络抖动、空闲连接被 wait_timeout 杀掉等场景下，
 * 无事务的请求自动重试成功，前端无感知。仅覆盖 primary 与动态数据源共用的 {@link Dao} 路径。
 * </p>
 *
 * <h3>重试判定（保守）</h3>
 * <ul>
 *   <li>{@link CannotGetJdbcConnectionException}：未从连接池拿到连接，SQL 必然未执行，重试绝对安全；</li>
 *   <li>{@link TransientDataAccessException}：Spring 约定的瞬时故障（连接中断、死锁回滚等）；</li>
 *   <li>根因 {@link SQLException} 的 sqlState 以 {@code 08} 开头（JDBC 连接异常类）；</li>
 *   <li>根因消息含 {@code Communications link failure} / {@code Connection refused} /
 *       {@code The last packet successfully received}（MySQL 断连典型消息）。</li>
 * </ul>
 *
 * <h3>不重试的场景</h3>
 * <ul>
 *   <li>当前线程存在活动事务（{@link TransactionSynchronizationManager#isActualTransactionActive()}）：
 *       事务内可能已执行部分写入，重试会导致重复执行，应交由上层回滚；</li>
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
     * 执行 action，连接类故障时按固化策略透明重试；重试耗尽或故障不可重试时抛出最后一次的 {@link DataAccessException}，
     * 由调用方包装为 {@link SqlExecuteException}。
     */
    public static <T> T execute(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return action.get();
        }
        DataAccessException last = null;
        for (int attempt = 0; attempt <= DEFAULT_MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                long waitMillis = DEFAULT_BACKOFF[Math.min(attempt - 1, DEFAULT_BACKOFF.length - 1)];
                log.warn("JDBC连接类异常，进行第 {}/{} 次重试（退避 {}ms），原因：{}",
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
     * 判断 DataAccessException 是否属于连接类瞬时故障（可安全重试）。
     */
    public static boolean isRetryable(DataAccessException e) {
        if (e instanceof CannotGetJdbcConnectionException || e instanceof TransientDataAccessException) {
            return true;
        }
        Throwable root = NestedExceptionUtils.getRootCause(e);
        if (root instanceof SQLException sqlException) {
            String sqlState = sqlException.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                return true;
            }
        }
        String message = root != null && root.getMessage() != null ? root.getMessage() : e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            return lower.contains("communications link failure")
                    || lower.contains("connection refused")
                    || lower.contains("the last packet successfully received");
        }
        return false;
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
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        String message = root.getMessage();
        if (message != null && message.length() > REASON_MAX_LENGTH) {
            message = message.substring(0, REASON_MAX_LENGTH) + "...";
        }
        return e.getClass().getSimpleName() + ": " + message;
    }
}
