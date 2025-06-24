package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.sql.SqlScriptParser;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.RuleService;
import org.apache.logging.log4j.util.Strings;
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
//        setDao(initDefaultDao(null));
    }

    private Dao initDefaultDao(String connectId) {
        DataSource ds;
        if (Strings.isNotBlank(connectId)) {
            ds = DataSourceManager.singleInstance().getDataSource(connectId);
        } else {
            ds = (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }

    /**
     * 执行与给定SQL键关联的SQL语句，并返回执行结果。
     *
     * @param sqlKey    SQL键，用于标识要执行的SQL语句
     * @param connectId 连接ID，用于标识要使用的数据库连接
     * @param params    SQL语句中所需的参数，以键值对的形式提供
     * @return SQL语句的执行结果
     * @throws ScriptException       如果在执行SQL语句时发生脚本异常，则抛出此异常
     * @throws NoSuchMethodException 如果在尝试执行SQL语句时找不到对应的方法，则抛出此异常
     */
    public Object executeSqlKey(String sqlKey, String connectId, Map<String, Object> params) throws ScriptException, NoSuchMethodException {
        return this.initDefaultDao(connectId).executeKey(sqlKey, params);
    }

    /**
     * 查询字典项
     * <p>
     * 根据字典ID查询对应的字典项列表。
     *
     * @param dictId 字典ID
     * @return 返回查询结果的ApiResult对象，包含字典项列表
     */
    public ApiResult<?> queryDictItems(String dictId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", dictId);
            params.put("enableStatus", 1);
            List<DictItem> list = this.initDefaultDao(null).queryList(DictItem.class, params, "seqNo asc");
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
