package cn.geelato.core.orm;

import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.meta.EntityManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.script.db.DbScriptManager;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.core.script.sql.SqlScriptManager;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.core.util.EncryptUtils;
import lombok.Getter;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseDao {
    protected final SqlScriptManager sqlScriptManager = SqlScriptManagerFactory.get("sql");
    protected final DbScriptManager dbScriptManager = DbScriptManagerFactory.get("db");

    @Getter
    protected JdbcTemplate jdbcTemplate;

    protected final MetaManager metaManager = MetaManager.singleInstance();
    protected final SqlManager sqlManager = SqlManager.singleInstance();
    protected final EntityManager entityManager = EntityManager.singleInstance();
    protected static final Map<String, Object> defaultParams = new HashMap<>();

    protected List<Map<String, Object>> queryForMapListInner(BoundSql boundSql) throws DataAccessException {
        try {
            // 每查询解析一次加密列集合（主实体+join 实体）；解析不出即空集——元数据不可用时加密侧从未加密过，不解密
            Set<String> encryptedColumns = EncryptedColumns.from(boundSql);
            return JdbcRetryExecutor.execute(() -> {
                if (boundSql.getTypes() != null && boundSql.getTypes().length > 0) {
                    return jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), boundSql.getTypes(), new DecryptingRowMapper(encryptedColumns));
                }
                return jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), new DecryptingRowMapper(encryptedColumns));
            });
        } catch (DataAccessException dataAccessException) {
            throw SqlExecuteException.of(dataAccessException, boundSql.getSql(), boundSql.getParams());
        }

    }
}

class DecryptingRowMapper implements RowMapper<Map<String, Object>> {
    /**
     * 仅对集合内（元数据标记加密）的列尝试解密；空集即无加密列，不做任何解密。
     * 与加密侧 EncryptInner 的元数据门控对称：解密需要元数据明确背书。
     */
    private final Set<String> encryptedColumns;

    DecryptingRowMapper(Set<String> encryptedColumns) {
        this.encryptedColumns = encryptedColumns == null ? Set.of() : encryptedColumns;
    }

    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof String && encryptedColumns.contains(columnName)) {
                row.put(columnName, EncryptUtils.decrypt(value.toString()));
            } else {
                row.put(columnName, value);
            }
        }
        return row;
    }

}
