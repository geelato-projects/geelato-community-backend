package cn.geelato.core.script.db;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DbScriptManager extends AbstractScriptManager {

    private final Map<String, String> sqlMap = new HashMap<>();
    private final Map<String, String> sqlResponseMap = new HashMap<>();

    public String getSqlResponse(String sqlKey) {
        if (!__CACHE__)
            refresh(sqlKey);
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
        String selectSql = null;
        if (StringUtils.isEmpty(sqlKey)) {
            selectSql = "select key_name,encoding_content,response_type from platform_sql where enable_status=1 and del_status=0";
        } else {
            selectSql = String.format("select key_name,encoding_content,response_type from platform_sql where enable_status=1 and del_status=0 and key_name='%s'", sqlKey);
        }
        List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(selectSql);
        for (Map<String, Object> map : list) {
            initOrRefreshMap(map);
        }
    }

    @Override
    public void loadDb() {
        String sql = "select key_name,encoding_content,response_type  from platform_sql where enable_status=1 and del_status=0";
        List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(sql);
        for (Map<String, Object> map : list) {
            initOrRefreshMap(map);
        }

    }

    private void initOrRefreshMap(Map<String, Object> map) {
        String key = null;
        String content = null;
        String response = "null";
        if (map.get("key_name") != null)
            key = map.get("key_name").toString();
        if (map.get("response_type") != null)
            content = map.get("encoding_content").toString();
        if (map.get("response_type") != null)
            response = map.get("response_type").toString();
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
}
