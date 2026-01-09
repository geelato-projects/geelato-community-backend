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
        try{
            return jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(), new DecryptingRowMapper());
        }catch (DataAccessException dataAccessException){
            throw new SqlExecuteException(dataAccessException,boundSql.getSql(),boundSql.getParams());
        }

    }
}

class DecryptingRowMapper implements RowMapper<Map<String, Object>> {
    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof String) {
                row.put(columnName, EncryptUtils.decrypt(value.toString()));
            } else {
                row.put(columnName, value);
            }
        }
        return row;
    }

}


