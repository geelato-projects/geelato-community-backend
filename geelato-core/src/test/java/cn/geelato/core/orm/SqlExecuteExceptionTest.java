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

    // ==================== 分类工厂：数据值类（10025-10027，用户文案含字段名） ====================

    @Test
    void mysqlDataTooLongMapsToDataTooLongWithColumnName() {
        // 生产实例：MySQL 1406 "Data truncation: Data too long for column 'ams_declaration' at row 1"
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Data truncation: Data too long for column 'ams_declaration' at row 1", "22001", 1406)), SQL, PARAMS);

        assertInstanceOf(SqlDataTooLongException.class, ex);
        assertEquals(SqlDataTooLongException.ERROR_CODE, ex.getErrorCode());
        assertEquals("字段[ams_declaration]的内容超出长度限制，请缩短后重试", ex.getUserMessage());
    }

    @Test
    void pgDataTooLongMapsToDataTooLongWithGenericMessage() {
        // PG 消息不含字段名：value too long for type character varying(20)
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("value too long for type character varying(20)", "22001", 0)), SQL, PARAMS);

        assertInstanceOf(SqlDataTooLongException.class, ex);
        assertEquals("数据内容超出字段长度限制，请缩短后重试", ex.getUserMessage());
    }

    @Test
    void mysqlOutOfRangeMapsToDataOutOfRangeWithColumnName() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Out of range value for column 'amount' at row 1", "22003", 1690)), SQL, PARAMS);

        assertInstanceOf(SqlDataOutOfRangeException.class, ex);
        assertEquals(SqlDataOutOfRangeException.ERROR_CODE, ex.getErrorCode());
        assertEquals("字段[amount]的数值超出允许范围，请调整后重试", ex.getUserMessage());
    }

    @Test
    void pgOutOfRangeMapsToDataOutOfRange() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("numeric field overflow", "22003", 0)), SQL, PARAMS);

        assertInstanceOf(SqlDataOutOfRangeException.class, ex);
    }

    @Test
    void mysqlIncorrectStringMapsToDataFormatWithColumnName() {
        // MySQL 1366 的 sqlState 是 HY000，按厂商码判定
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Incorrect string value: '\\xF0\\x9F\\x98\\x80' for column 'remark' at row 1", "HY000", 1366)), SQL, PARAMS);

        assertInstanceOf(SqlDataFormatException.class, ex);
        assertEquals(SqlDataFormatException.ERROR_CODE, ex.getErrorCode());
        assertEquals("字段[remark]的数据格式不正确，请检查填写内容后重试", ex.getUserMessage());
    }

    @Test
    void mysqlIncorrectDateMapsToDataFormatWithColumnName() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("Incorrect date value: '2026-13-01' for column 'etd' at row 1", "22007", 1292)), SQL, PARAMS);

        assertInstanceOf(SqlDataFormatException.class, ex);
        assertEquals("字段[etd]的数据格式不正确，请检查填写内容后重试", ex.getUserMessage());
    }

    @Test
    void pgInvalidDatetimeMapsToDataFormat() {
        SqlExecuteException ex = SqlExecuteException.of(
                wrapping(new SQLException("date/time field value out of range", "22008", 0)), SQL, PARAMS);

        assertInstanceOf(SqlDataFormatException.class, ex);
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
