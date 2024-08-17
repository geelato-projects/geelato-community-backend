package cn.geelato.core.script.db;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j

public class DbScriptManager extends AbstractScriptManager {

    private final Map<String,String> sqlMap=new HashMap<>();

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

    @SuppressWarnings("ALL")
    public void refresh(String sqlKey){
        String selectSql=null;
        if(StringUtils.isEmpty(sqlKey)){
            selectSql="select key_name,encoding_content from platform_sql where enable_status=1 and del_status=0";
        }else{
            selectSql=String.format("select key_name,encoding_content from platform_sql where enable_status=1 and del_status=0 and key_name='%'",sqlKey);
        }
        List<Map<String,Object>> list = dao.getJdbcTemplate().queryForList(selectSql);
        for (Map<String,Object> map : list) {
            String key= map.get("key_name").toString();
            String content= map.get("encoding_content").toString();
            if(map.containsKey(key)){
                if(validateContent(content)){
                    map.replace(key,content);
                }
            }else {
                if(validateContent(content)){
                    map.put(key,content);
                }
            }
        }
    }
    @Override
    public void loadDb() {
        String sql="select key_name,encoding_content from platform_sql where enable_status=1 and del_status=0";
        List<Map<String,Object>> list = dao.getJdbcTemplate().queryForList(sql);
        for (Map<String,Object> map : list) {
            String key= map.get("key_name").toString();
            Object content=map.get("encoding_content");
            if(validateContent(content)){
                sqlMap.put(key,content.toString());
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
