package cn.geelato.orm.executor.support;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.orm.SqlExecuteException;
import cn.geelato.core.util.EncryptUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ORM 模块内的 BoundSql + JdbcTemplate 执行支持。
 * 先在 ORM 内部收口 direct-jdbc 语义，避免引入对 core 重新编译的额外依赖。
 */
public class BoundSqlJdbcSupport {

    private final JdbcTemplate jdbcTemplate;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final RowMapper<Map<String, Object>> decryptingRowMapper = new DecryptingRowMapper();

    public BoundSqlJdbcSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> queryForMap(BoundSql boundSql) {
        return execute(boundSql, () -> {
            List<Map<String, Object>> rows = boundSql.getTypes() != null && boundSql.getTypes().length > 0
                    ? jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), decryptingRowMapper)
                    : jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), decryptingRowMapper);
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    public <T> T queryForObject(BoundSql boundSql, Class<T> requiredType) {
        return execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.queryForObject(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), requiredType)
                : jdbcTemplate.queryForObject(boundSql.getSql(), requiredType, boundSql.getParams()));
    }

    public List<Map<String, Object>> queryForMapList(BoundPageSql boundPageSql) {
        BoundSql boundSql = boundPageSql.getBoundSql();
        List<Map<String, Object>> rows = execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), decryptingRowMapper)
                : jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), decryptingRowMapper));
        return convert(rows, resolveEntityMeta(boundSql));
    }

    public Long queryTotal(BoundPageSql boundPageSql) {
        BoundSql boundSql = boundPageSql.getBoundSql();
        return queryForLong(boundPageSql.getCountSql(), boundSql.getParams(), boundSql.getTypes());
    }

    public <T> List<T> queryForOneColumnList(BoundSql boundSql, Class<T> elementType) {
        return execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.queryForList(boundSql.getSql(), elementType, boundSql.getParams(), boundSql.getTypes())
                : jdbcTemplate.queryForList(boundSql.getSql(), elementType, boundSql.getParams()));
    }

    public List<Map<String, Object>> callForMapList(String callSql, Object[] params) {
        try {
            return jdbcTemplate.query(callSql, decryptingRowMapper, params);
        } catch (DataAccessException ex) {
            throw new SqlExecuteException(ex, callSql, params);
        }
    }

    public Map<String, Object> callForMap(String callSql, Object[] params) {
        List<Map<String, Object>> rows = callForMapList(callSql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> nativeQueryForMapList(String sql, Object[] params) {
        try {
            return jdbcTemplate.query(sql, decryptingRowMapper, params);
        } catch (DataAccessException ex) {
            throw new SqlExecuteException(ex, sql, params);
        }
    }

    public Map<String, Object> nativeQueryForMap(String sql, Object[] params) {
        List<Map<String, Object>> rows = nativeQueryForMapList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> T nativeQueryForObject(String sql, Object[] params, Class<T> requiredType) {
        try {
            return jdbcTemplate.queryForObject(sql, requiredType, params);
        } catch (DataAccessException ex) {
            throw new SqlExecuteException(ex, sql, params);
        }
    }

    public int nativeExecute(String sql, Object[] params) {
        try {
            return jdbcTemplate.update(sql, params);
        } catch (DataAccessException ex) {
            throw new SqlExecuteException(ex, sql, params);
        }
    }

    public int executeUpdate(BoundSql boundSql) {
        return execute(boundSql, () -> boundSql.getTypes() != null && boundSql.getTypes().length > 0
                ? jdbcTemplate.update(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes())
                : jdbcTemplate.update(boundSql.getSql(), boundSql.getParams()));
    }

    private <T> T execute(BoundSql boundSql, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            throw new SqlExecuteException(ex, boundSql.getSql(), boundSql.getParams());
        }
    }

    private Long queryForLong(String sql, Object[] params, int[] types) {
        if (types != null && types.length > 0) {
            List<Long> result = jdbcTemplate.query(sql,
                    ps -> {
                        for (int i = 0; i < params.length; i++) {
                            ps.setObject(i + 1, params[i], types[i]);
                        }
                    },
                    (rs, rowNum) -> rs.getLong(1));
            return result.isEmpty() ? 0L : result.get(0);
        }
        return jdbcTemplate.queryForObject(sql, Long.class, params);
    }

    private EntityMeta resolveEntityMeta(BoundSql boundSql) {
        if (boundSql == null || boundSql.getCommand() == null) {
            return null;
        }
        if (boundSql.getCommand() instanceof QueryCommand command) {
            return metaManager.getByEntityName(command.getEntityName());
        }
        return null;
    }

    private List<Map<String, Object>> convert(List<Map<String, Object>> data, EntityMeta entityMeta) {
        if (data == null || data.isEmpty() || entityMeta == null) {
            return data;
        }
        for (Map<String, Object> row : data) {
            for (String key : row.keySet()) {
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);
                if (fieldMeta == null) {
                    continue;
                }
                if (!"JSON".equals(fieldMeta.getColumnMeta().getDataType())) {
                    continue;
                }
                Object value = row.get(key);
                String text = value != null ? value.toString() : "";
                if (text.startsWith("{") && text.endsWith("}")) {
                    row.put(key, JSONObject.parse(text));
                } else if (text.startsWith("[") && text.endsWith("]")) {
                    row.put(key, JSONArray.parse(text));
                }
            }
        }
        return data;
    }

    private static class DecryptingRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> row = new HashMap<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value instanceof String textValue) {
                    row.put(columnName, EncryptUtils.decrypt(textValue));
                } else {
                    row.put(columnName, value);
                }
            }
            return row;
        }
    }
}
