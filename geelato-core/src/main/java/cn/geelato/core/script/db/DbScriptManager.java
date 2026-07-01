package cn.geelato.core.script.db;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DbScriptManager extends AbstractScriptManager {
    private final Map<String, String> sqlMap = new HashMap<>();
    private final Map<String, String> sqlResponseMap = new HashMap<>();

    public String getSqlResponse(String sqlKey) {
        if (!__CACHE__) {
            refresh(sqlKey);
        }
        return sqlResponseMap.get(sqlKey);
    }

    public String generate(String id, Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        if (sqlMap.containsKey(id)) {
            String sql = sqlMap.get(id);
            if (paramMap != null) {
                for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                    sql = sql.replace("$." + entry.getKey(), entry.getValue().toString());
                }
            }
            if (log.isInfoEnabled()) {
                log.info("sql {} : {}", id, sql);
            }
            return sql;
        } else {
            log.error("未找到sqlId：{} 对应的语句。", id);
            return null;
        }
    }


    @SuppressWarnings("ALL")
    public void refresh(String sqlKey) {
        if (!isScriptTableAvailable()) {
            return;
        }
        String selectSql = null;
        if (StringUtils.isEmpty(sqlKey)) {
            selectSql = "select key_name,encoding_content,response_type from platform_sql where enable_status=1 and del_status=0";
        } else {
            selectSql = String.format("select key_name,encoding_content,response_type from platform_sql where enable_status=1 and del_status=0 and key_name='%s'", sqlKey);
        }
        try {
            List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(selectSql);
            for (Map<String, Object> map : list) {
                initOrRefreshMap(map);
            }
        } catch (DataAccessException ex) {
            log.warn("Skip loading DB scripts because table platform_sql is unavailable: {}", ex.getMessage());
        }
    }

    @Override
    public void loadDb() {
        if (!isScriptTableAvailable()) {
            return;
        }
        String sql = "select key_name,encoding_content,response_type from platform_sql where enable_status=1 and del_status=0";
        try {
            List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(sql);
            for (Map<String, Object> map : list) {
                initOrRefreshMap(map);
            }
        } catch (DataAccessException ex) {
            log.warn("Skip loading DB scripts because table platform_sql is unavailable: {}", ex.getMessage());
        }
    }

    private void initOrRefreshMap(Map<String, Object> map) {
        String key = null;
        String content = null;
        String response = "null";
        if (map.get("key_name") != null) {
            key = map.get("key_name").toString();
        }
        if (map.get("response_type") != null) {
            content = map.get("encoding_content").toString();
        }
        if (map.get("response_type") != null) {
            response = map.get("response_type").toString();
        }
        if (validateContent(content)) {
            if (sqlMap.containsKey(key)) {
                sqlMap.replace(key, content);
                sqlResponseMap.replace(key, response);
            } else {
                sqlMap.put(key, content);
                sqlResponseMap.put(key, response);
            }
        }

    }

    @Override
    public void parseFile(File file) throws IOException {

    }

    @Override
    public void parseStream(InputStream is) throws IOException {

    }

    private boolean isScriptTableAvailable() {
        if (dao == null || dao.getJdbcTemplate() == null || dao.getJdbcTemplate().getDataSource() == null) {
            log.warn("Skip DB script loading because Dao or DataSource is not ready.");
            return false;
        }
        try (Connection connection = dao.getJdbcTemplate().getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (tableExists(metaData, "platform_sql")) {
                return true;
            }
            log.info("Skip DB script loading because table platform_sql does not exist.");
            return false;
        } catch (SQLException | RuntimeException ex) {
            log.warn("Skip DB script loading because table platform_sql cannot be inspected: {}", ex.getMessage());
            return false;
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet tables = metaData.getTables(null, null, tableName, null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metaData.getTables(null, null, tableName.toLowerCase(), null)) {
            return tables.next();
        }
    }
}
