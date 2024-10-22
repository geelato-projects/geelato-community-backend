package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.sql.SqlScriptParser;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.enums.ResponseParamTypeEnum;
import cn.geelato.web.platform.m.base.service.RuleService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
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
        return this.initDefaultDao().executeKey(sqlKey,params);
//        Object result = null;
//        try {
//            if (StringUtils.isBlank(sqlKey)) {
//                throw new Exception("sqlKey is null");
//            }
//            // 获取sql
//            String sql = String.format("select * from platform_sql where key_name='%s' and del_status=0", sqlKey);
//            List<Map<String, Object>> list = initDefaultDao().getJdbcTemplate().queryForList(sql);
//            if (list == null || list.size() == 0) {
//                throw new Exception("Sql not found");
//            }
//            if (list.size() > 1) {
//                throw new Exception("Sql duplicate");
//            }
//            // 获取sql编码内容
//            Map<String, Object> map = list.get(0);
//            Object content = map.get("encoding_content");
//            if (content == null || StringUtils.isEmpty(String.valueOf(content))) {
//                throw new Exception("Sql encodingContent is null");
//            }
//            Object responseType = map.get("response_type");
//            if (responseType == null || StringUtils.isBlank(String.valueOf(responseType))) {
//                throw new Exception("Sql ResponseType is empty");
//            }
//            // 获取sql编码内容对应的js函数
//            String jsFunction = javaScriptFunction(String.valueOf(content));
//            if (StringUtils.isBlank(jsFunction)) {
//                throw new Exception("Sql JavaScript Function is empty");
//            }
//            // 参数处理
//            Map<String, Object> mixParam = mixParam(params);
//            // 执行js函数
//            Context context = Context.newBuilder("js").allowAllAccess(true).build();
//            Value value = context.eval("js", "(" + jsFunction + ")");
//            if (value == null || !value.canExecute()) {
//                throw new Exception("Sql JavaScript Function Execute error");
//            }
//            String jsResult = value.execute(mixParam).asString();
//            if (StringUtils.isBlank(jsResult)) {
//                throw new Exception("Sql JavaScript Function Result error");
//            }
//            // 执行sql
//            String resType = String.valueOf(responseType);
//            if (ResponseParamTypeEnum.NULL.name().equalsIgnoreCase(resType)) {
//                initDefaultDao().getJdbcTemplate().execute(jsResult);
//            } else if (ResponseParamTypeEnum.STRING.name().equalsIgnoreCase(resType) ||
//                    ResponseParamTypeEnum.NUMBER.name().equalsIgnoreCase(resType) ||
//                    ResponseParamTypeEnum.BOOLEAN.name().equalsIgnoreCase(resType) ||
//                    ResponseParamTypeEnum.OBJECT.name().equalsIgnoreCase(resType)) {
//                result = initDefaultDao().getJdbcTemplate().queryForMap(jsResult);
//            } else if (ResponseParamTypeEnum.ARRAY.name().equalsIgnoreCase(resType)) {
//                result = initDefaultDao().getJdbcTemplate().queryForList(jsResult);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//
//        return result;
    }

    /**
     * js函数,将sql内容转换为js函数
     *
     * @param encodingContent 编码内容
     * @return
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
