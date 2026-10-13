package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 非预期异常（10028）：未纳入已知分类（10021-10027）的所有 SQL 故障兜底。
 * <p>如 MySQL 1052（错误码→SQLSTATE 按段机械映射会伪装成 23xxx 约束码，但本质是用法错误）、
 * 1064 语法错误、权限不足等。这类根因无法从厂商码/状态码可靠归类，
 * 统一落本异常，避免被约束等分类误报；由 {@link SqlExecuteException#of} 分类工厂创建。
 * 根类 {@link SqlExecuteException}（10002）不再作为兜底返回值使用。</p>
 */
public class SqlUnexpectedException extends SqlExecuteException {

    public static final int ERROR_CODE = 10028;

    public SqlUnexpectedException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        return "数据操作出现未预期错误，已记录日志，请联系管理员排查";
    }
}
