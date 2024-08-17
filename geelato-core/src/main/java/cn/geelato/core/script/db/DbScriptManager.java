package cn.geelato.core.script.db;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DbScriptManager extends AbstractScriptManager {

    private Map<String,String> sqlMap=new HashMap<>();

    public String generate(String id, Map<String, Object> paramMap) {
        if (sqlMap.containsKey(id)) {
                String sql = sqlMap.get(id);
                if (log.isInfoEnabled()) {
                    log.info("sql {} : {}", id, sql);
                }
                return sql;
        } else {
            Assert.isTrue(false, "未找到sqlId：" + id + "，对应的语句。");
            return null;
        }
    }

    @Override
    public void loadDb() {
        String sql="select key_name,encoding_content from platform_sql where enable_status=1 and del_status=0";
        Map<String,Object> map = dao.getJdbcTemplate().queryForMap(sql);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if(validateContent(value)){
                sqlMap.put(key,value.toString());
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
