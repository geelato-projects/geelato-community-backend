package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.sql.SqlScriptParser;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.RuleService;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.script.ScriptException;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GraalService(name = "dao", built = "true")
public class GqlService extends RuleService {
    private final SqlScriptParser sqlScriptParser = new SqlScriptParser();
    private static final String EXECUTE_SQL_KEY = "execute_sql_key";

    public GqlService() {
        setDao(initDefaultDao());
    }

    private Dao initDefaultDao() {
        DataSource ds = (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }

    public Object executeSqlKey(String sqlKey, Map<String, Object> params) throws ScriptException, NoSuchMethodException {
        return this.initDefaultDao().executeKey(sqlKey, params);
    }

    /**
     * 查询字典项
     *
     * @param dictId
     * @return
     */
    public ApiResult queryDictItems(String dictId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", dictId);
            params.put("enableStatus", 1);
            List<DictItem> list = this.initDefaultDao().queryList(DictItem.class, params, "seqNo asc");
            return ApiResult.success(list);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * js函数,将sql内容转换为js函数
     *
     * @param encodingContent 编码内容
     */
    private String javaScriptFunction(String encodingContent) {
        List<String> lines = new ArrayList<>();
        lines.add("-- @sql " + EXECUTE_SQL_KEY);
        lines.add(encodingContent);
        Map<String, String> map = sqlScriptParser.parse(lines);
        if (map == null || !map.containsKey(EXECUTE_SQL_KEY)) {
            throw new RuntimeException("encodingContent to javaScript Function error");
        }
        return map.get(EXECUTE_SQL_KEY);
    }

    private Map<String, Object> mixParam(Map<String, Object> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            paramMap = new HashMap<>();
        }
        return paramMap;
    }
}
