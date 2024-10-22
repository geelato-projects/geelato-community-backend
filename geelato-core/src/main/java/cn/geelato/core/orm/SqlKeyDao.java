package cn.geelato.core.orm;

import javax.script.ScriptException;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
public class SqlKeyDao extends SqlIdDao{

    public Object executeKey(String sqlKey, Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        switch (dbScriptManager.getSqlResponse(sqlKey).toLowerCase()){
            case "map":
                return queryForMapKey(sqlKey,paramMap);
            case "list":
                return queryForListKey(sqlKey,paramMap);
            default:
                executeForNull(sqlKey,paramMap);
                return null;
        }
    }
    public void executeForNull(String sqlKey, Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        jdbcTemplate.execute(dbScriptManager.generate(sqlKey, paramMap));
    }

    public Map queryForMapKey(String sqlKey,Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        return jdbcTemplate.queryForMap(dbScriptManager.generate(sqlKey, paramMap));

    }
    public List queryForListKey(String sqlKey,Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        return jdbcTemplate.queryForList(dbScriptManager.generate(sqlKey, paramMap));
    }
}
