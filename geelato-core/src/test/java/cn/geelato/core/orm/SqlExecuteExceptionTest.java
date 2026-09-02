package cn.geelato.core.orm;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExecuteExceptionTest {

    private static final String SQL = "select * from platform_dev_table where id = ?";
    private static final Object[] PARAMS = {"123"};

    /** UncategorizedDataAccessException 是抽象类，用匿名子类包装 SQLException 根因。 */
    private static DataAccessException wrapping(SQLException root) {
        return new UncategorizedDataAccessException("query failed", root) {
        };
    }

    // ==================== 分类工厂：MySQL ====================

    @Test
    void cannotGetJdbcConnectionMapsToSqlConnection() {
        SqlExecuteException ex = SqlExecuteException.of(
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection"), SQL, PARAMS);

        assertInstanceOf(SqlConnectionException.class, ex);
        assertEquals(SqlConnectionException.ERROR_CODE, ex.getErrorCode());
        assertEquals("数据库连接中断，系统正在自动恢复，请稍后重试", ex.getUserMessage());
    }

    @Test
    void mysqlSqlState08MapsToSqlConnection() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Connection refused", "08S01", 1042)), SQL, PARAMS);

        assertInstanceOf(SqlConnectionException.class, ex);
    }

    @Test
    void mysqlCommunicationsLinkFailureMapsToSqlConnection() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Communications link failure due to underlying exception", "HY000", 0)), SQL, PARAMS);

        assertInstanceOf(SqlConnectionException.class, ex);
    }

    @Test
    void mysqlDeadlockMapsToLockConflict() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Deadlock found when trying to get lock", "40001", 1213)), SQL, PARAMS);

        assertInstanceOf(SqlLockConflictException.class, ex);
        assertEquals(SqlLockConflictException.ERROR_CODE, ex.getErrorCode());
        assertEquals("当前数据正被其他操作占用，请稍后重试", ex.getUserMessage());
    }

    @Test
    void mysqlLockWaitTimeoutMapsToLockConflict() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Lock wait timeout exceeded", "40001", 1205)), SQL, PARAMS);

        assertInstanceOf(SqlLockConflictException.class, ex);
    }

    @Test
    void mysqlDuplicateKeyMapsToDuplicateKey() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)), SQL, PARAMS);

        assertInstanceOf(SqlDuplicateKeyException.class, ex);
        assertEquals(SqlDuplicateKeyException.ERROR_CODE, ex.getErrorCode());
        assertEquals("数据已存在，无法重复提交", ex.getUserMessage());
    }

    @Test
    void mysqlForeignKeyMapsToConstraintViolation() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452)), SQL, PARAMS);

        assertInstanceOf(SqlConstraintViolationException.class, ex);
        assertEquals(SqlConstraintViolationException.ERROR_CODE, ex.getErrorCode());
        assertEquals("数据存在关联引用或不符合约束，请检查后重试", ex.getUserMessage());
    }

    @Test
    void mysqlSyntaxErrorFallsBackToRoot() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("You have an error in your SQL syntax", "42000", 1064)), SQL, PARAMS);

        assertEquals(SqlExecuteException.class, ex.getClass());
        assertEquals(SqlExecuteException.ERROR_CODE, ex.getErrorCode());
        assertEquals("数据操作失败，请稍后重试", ex.getUserMessage());
    }

    // ==================== 分类工厂：PostgreSQL（getErrorCode 恒为 0，仅靠 sqlState 判定） ====================

    @Test
    void pgConnectionFailureMapsToSqlConnection() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("An I/O error occurred while sending to the backend", "08006", 0)), SQL, PARAMS);

        assertInstanceOf(SqlConnectionException.class, ex);
    }

    @Test
    void pgDeadlockMapsToLockConflict() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("deadlock detected", "40P01", 0)), SQL, PARAMS);

        assertInstanceOf(SqlLockConflictException.class, ex);
    }

    @Test
    void pgLockNotAvailableMapsToLockConflict() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("could not obtain lock on row in relation", "55P03", 0)), SQL, PARAMS);

        assertInstanceOf(SqlLockConflictException.class, ex);
    }

    @Test
    void pgUniqueViolationMapsToDuplicateKey() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("duplicate key value violates unique constraint", "23505", 0)), SQL, PARAMS);

        assertInstanceOf(SqlDuplicateKeyException.class, ex);
    }

    @Test
    void pgForeignKeyViolationMapsToConstraintViolation() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("insert or update on table violates foreign key constraint", "23503", 0)), SQL, PARAMS);

        assertInstanceOf(SqlConstraintViolationException.class, ex);
    }

    @Test
    void pgNotNullViolationMapsToConstraintViolation() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("null value in column violates not-null constraint", "23502", 0)), SQL, PARAMS);

        assertInstanceOf(SqlConstraintViolationException.class, ex);
    }

    // ==================== 技术详情与子类继承 ====================

    @Test
    void technicalDetailsKeptInErrorMsgOnly() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)), SQL, PARAMS);

        // 技术详情（SQL/参数/厂商错误码）保留在 errorMsg，供服务端日志排障
        assertTrue(ex.getErrorMsg().contains(SQL));
        assertTrue(ex.getErrorMsg().contains("123"));
        assertTrue(ex.getErrorMsg().contains("1062"));
        // 用户可见文案不含 SQL 与参数
        assertFalse(ex.getUserMessage().contains("select"));
        assertFalse(ex.getUserMessage().contains("123"));
        // 富字段可从子类直接读取
        assertEquals("23000", ex.getSqlState());
        assertEquals(1062, ex.getDbErrorCode());
    }

    @Test
    void subclassesInheritDocSlugFromRoot() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)), SQL, PARAMS);

        assertEquals("sql-execute", ex.getDocSlug());
    }
}
