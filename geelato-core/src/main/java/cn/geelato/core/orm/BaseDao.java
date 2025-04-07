package cn.geelato.core.orm;

import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.meta.EntityManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.script.db.DbScriptManager;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.core.script.sql.SqlScriptManager;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.core.util.EncryptUtils;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseDao {
    protected final SqlScriptManager sqlScriptManager = SqlScriptManagerFactory.get("sql");
    protected final DbScriptManager dbScriptManager= DbScriptManagerFactory.get("db");

    protected JdbcTemplate jdbcTemplate;

    protected final MetaManager metaManager = MetaManager.singleInstance();
    protected final SqlManager sqlManager = SqlManager.singleInstance();
    protected final EntityManager entityManager = EntityManager.singleInstance();

    protected static final Map<String, Object> defaultParams = new HashMap<>();

    protected List<Map<String, Object>> queryForMapListInner(BoundSql boundSql) throws DataAccessException {
        return jdbcTemplate.query(boundSql.getSql(), boundSql.getParams(),new DecryptingRowMapper());
    }
}

class DecryptingRowMapper implements RowMapper<Map<String, Object>> {
    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        int columnCount = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = rs.getMetaData().getColumnName(i);
            Object value = rs.getObject(i);
            if (value instanceof String) {
                row.put(columnName, EncryptUtils.decrypt(value.toString()));
            }else{
                row.put(columnName, value);
            }
        }
        return row;
    }

}


