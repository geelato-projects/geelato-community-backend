package cn.geelato.core.orm;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExecuteExceptionTest {

    /** UncategorizedDataAccessException 是抽象类，用匿名子类包装 SQLException 根因。 */
    private static SqlExecuteException of(SQLException root) {
        DataAccessException dae = new UncategorizedDataAccessException("query failed", root) {
        };
        return new SqlExecuteException(dae, "select * from platform_dev_table where id = ?", new Object[]{"123"});
    }

    @Test
    void connectionFailureMapsToRecoveryMessage() {
        SqlExecuteException ex = new SqlExecuteException(
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection"),
                "select 1", null);

        assertEquals("数据库连接中断，系统正在自动恢复，请稍后重试", ex.getUserMessage());
    }

    @Test
    void sqlState08MapsToRecoveryMessage() {
        assertEquals("数据库连接中断，系统正在自动恢复，请稍后重试",
                of(new SQLException("Connection refused", "08S01", 1042)).getUserMessage());
    }

    @Test
    void communicationsLinkFailureMapsToRecoveryMessage() {
        assertEquals("数据库连接中断，系统正在自动恢复，请稍后重试",
                of(new SQLException("Communications link failure due to underlying exception")).getUserMessage());
    }

    @Test
    void deadlockMapsToLockConflictMessage() {
        assertEquals("当前数据正被其他操作占用，请稍后重试",
                of(new SQLException("Deadlock found when trying to get lock", "40001", 1213)).getUserMessage());
    }

    @Test
    void lockWaitTimeoutMapsToLockConflictMessage() {
        assertEquals("当前数据正被其他操作占用，请稍后重试",
                of(new SQLException("Lock wait timeout exceeded", "40001", 1205)).getUserMessage());
    }

    @Test
    void duplicateKeyMapsToDuplicateMessage() {
        assertEquals("数据已存在，无法重复提交",
                of(new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)).getUserMessage());
    }

    @Test
    void pgUniqueViolationMapsToDuplicateMessage() {
        assertEquals("数据已存在，无法重复提交",
                of(new SQLException("duplicate key value violates unique constraint", "23505", 0)).getUserMessage());
    }

    @Test
    void foreignKeyMapsToConstraintMessage() {
        assertEquals("数据存在关联引用或不符合约束，请检查后重试",
                of(new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452)).getUserMessage());
    }

    @Test
    void syntaxErrorMapsToDefaultMessage() {
        assertEquals("数据操作失败，请稍后重试",
                of(new SQLException("You have an error in your SQL syntax", "42000", 1064)).getUserMessage());
    }

    @Test
    void technicalDetailsKeptInErrorMsgOnly() {
        SqlExecuteException ex = of(new SQLException("You have an error in your SQL syntax", "42000", 1064));

        // 技术详情（SQL/参数/厂商错误码）保留在 errorMsg，供服务端日志排障
        assertTrue(ex.getErrorMsg().contains("select * from platform_dev_table"));
        assertTrue(ex.getErrorMsg().contains("123"));
        assertTrue(ex.getErrorMsg().contains("1064"));
        // 用户可见文案不含 SQL 与参数
        assertFalse(ex.getUserMessage().contains("select"));
        assertFalse(ex.getUserMessage().contains("123"));
    }
}
