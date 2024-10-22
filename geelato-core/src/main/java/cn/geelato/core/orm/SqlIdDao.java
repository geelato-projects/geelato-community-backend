package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
public class SqlIdDao extends BaseDao{

    public void execute(String sqlId, Map<String, Object> paramMap) {
        jdbcTemplate.execute(sqlScriptManager.generate(sqlId, paramMap));
    }

    public Map<String, Object> queryForMap(String sqlId, Map<String, Object> paramMap) throws DataAccessException {
        return jdbcTemplate.queryForMap(sqlScriptManager.generate(sqlId, mixParam(paramMap)));
    }

    public <T> T queryForObject(String sqlId, Map<String, Object> paramMap, Class<T> requiredType) throws DataAccessException {
        return jdbcTemplate.queryForObject(sqlScriptManager.generate(sqlId, mixParam(paramMap)), requiredType);
    }

    public List<Map<String, Object>> queryForMapList(String sqlId, Map<String, Object> paramMap) {
        return jdbcTemplate.queryForList(sqlScriptManager.generate(sqlId, mixParam(paramMap)));
    }

    public <T> List<T> queryForOneColumnList(String sqlId, Map<String, Object> paramMap, Class<T> elementType) throws DataAccessException {
        return jdbcTemplate.queryForList(sqlScriptManager.generate(sqlId, mixParam(paramMap)), elementType);
    }

    public int save(String sqlId, Map<String, Object> paramMap) {
        return jdbcTemplate.update(sqlScriptManager.generate(sqlId, mixParam(paramMap)));
    }

    private Map<String, Object> mixParam(Map<String, Object> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            paramMap = new HashMap<>();
        }
        paramMap.put("$ctx", defaultParams.get("$ctx"));
        return paramMap;
    }
}
