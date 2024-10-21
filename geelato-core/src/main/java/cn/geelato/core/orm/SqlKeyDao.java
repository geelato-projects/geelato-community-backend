package cn.geelato.core.orm;

import cn.geelato.core.script.db.DbScriptManager;

import javax.script.ScriptException;
import java.util.Map;

@SuppressWarnings("ALL")
public class SqlKeyDao extends SqlIdDao{

    public void executeKey(String sqlKey, Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        jdbcTemplate.execute(dbScriptManager.generate(sqlKey, paramMap));
    }
}
