package cn.geelato.core.orm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRetryExecutorTest {

    @BeforeEach
    void resetTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @AfterEach
    void clearTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /** UncategorizedDataAccessException 是抽象类，用匿名子类包装 SQLException 根因。 */
    private static DataAccessException wrapping(SQLException root) {
        return new UncategorizedDataAccessException("query failed", root) {
        };
    }

    // ==================== isRetryable 判定 ====================

    @Test
    void cannotGetJdbcConnectionIsRetryable() {
        assertTrue(JdbcRetryExecutor.isRetryable(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection")));
    }

    @Test
    void transientDataAccessIsRetryable() {
        assertTrue(JdbcRetryExecutor.isRetryable(new TransientDataAccessResourceException("connection reset")));
    }

    @Test
    void sqlState08IsRetryable() {
        SQLException sqlException = new SQLException("Connection refused", "08S01", 1042);
        assertTrue(JdbcRetryExecutor.isRetryable(wrapping(sqlException)));
    }

    @Test
    void communicationsLinkFailureMessageIsRetryable() {
        SQLException sqlException = new SQLException("Communications link failure due to underlying exception");
        assertTrue(JdbcRetryExecutor.isRetryable(wrapping(sqlException)));
    }

    @Test
    void connectionRefusedMessageWithoutCauseIsRetryable() {
        DataAccessException noCause = new DataAccessException("Connection refused") {
        };
        assertTrue(JdbcRetryExecutor.isRetryable(noCause));
    }

    @Test
    void constraintViolationIsNotRetryable() {
        SQLException sqlException = new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062);
        assertFalse(JdbcRetryExecutor.isRetryable(wrapping(sqlException)));
    }

    @Test
    void springDuplicateKeyIsNotRetryable() {
        assertFalse(JdbcRetryExecutor.isRetryable(new DuplicateKeyException("Duplicate entry")));
    }

    // ==================== execute 行为 ====================

    @Test
    void succeedsOnFirstAttemptWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();

        String result = JdbcRetryExecutor.execute(() -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void retriesAndSucceedsOnConnectionFailure() {
        AtomicInteger attempts = new AtomicInteger();

        String result = JdbcRetryExecutor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(DuplicateKeyException.class, () -> JdbcRetryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new DuplicateKeyException("Duplicate entry");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void exhaustsRetriesThenThrowsLastFailure() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(CannotGetJdbcConnectionException.class, () -> JdbcRetryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection");
        }));
        // 固化策略 max-attempts=2：首次 + 2 次重试 = 3 次
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryInsideActiveTransaction() {
        AtomicInteger attempts = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThrows(CannotGetJdbcConnectionException.class, () -> JdbcRetryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection");
        }));
        assertEquals(1, attempts.get());
    }
}
