package cn.geelato.web.platform.graal.service;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.sql.SqlScriptParser;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.meta.Entity;
import cn.geelato.meta.DictItem;
import cn.geelato.web.platform.srv.base.service.RuleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.script.ScriptException;
import javax.sql.DataSource;
import java.util.*;

@GraalService(name = "dao", built = "true")
public class GqlService extends RuleService {
    private final SqlScriptParser sqlScriptParser = new SqlScriptParser();
    private static final String EXECUTE_SQL_KEY = "execute_sql_key";

    public GqlService() {
//        setDao(initDefaultDao(null));
    }

    private Dao initDefaultDao(String connectId) {
        if (StringUtils.isBlank(connectId)) {
            connectId = getPrimaryConnectId();
        }
        DataSource ds = DataSourceManager.singleInstance().getDataSource(connectId);
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
     * 查询字典项列表
     * <p>
     * 根据字典ID查询对应的字典项列表，可选项按itemValue筛选
     *
     * @param dictId    字典ID（必填）
     * @param itemValue 字典项值（可选，为null时查询全部）
     * @return 返回查询结果的ApiResult对象，包含字典项列表
     * @throws IllegalArgumentException 如果dictId为空
     */
    public ApiResult<List<DictItem>> queryDictItems(String dictId, Object itemValue) {
        // 参数校验
        if (StringUtils.isBlank(dictId)) {
            throw new IllegalArgumentException("字典ID不能为空");
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", dictId);
            params.put("enableStatus", 1);
            // 如果传入了itemValue，则添加过滤条件
            if (itemValue != null) {
                params.put("itemValue", itemValue);
            }
            List<DictItem> list = this.initDefaultDao(null).queryList(DictItem.class, params, "seqNo asc");
            return ApiResult.success(list);
        } catch (Exception e) {
            return ApiResult.fail("查询字典项失败: " + e.getMessage());
        }
    }

    /**
     * 查询字典项列表（简化版）
     * <p>
     * 根据字典ID查询所有有效的字典项列表
     *
     * @param dictId 字典ID（必填）
     * @return 返回查询结果的ApiResult对象，包含字典项列表
     */
    public Object queryDictItems(String dictId) {
        return queryDictItems(dictId, null);
    }

    /**
     * 查询单个字典项
     * <p>
     * 根据字典ID和字典项值查询特定的字典项
     *
     * @param dictId    字典ID（必填）
     * @param itemValue 字典项值（必填）
     * @return 返回查询结果的ApiResult对象，包含单个字典项
     * @throws IllegalArgumentException 如果dictId或itemValue为空
     */
    public ApiResult<DictItem> queryDictItem(String dictId, Object itemValue) {
        if (itemValue == null) {
            throw new IllegalArgumentException("字典项值不能为空");
        }
        ApiResult<List<DictItem>> result = queryDictItems(dictId, itemValue);
        if (!result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
            return ApiResult.fail("未找到匹配的字典项");
        }
        return ApiResult.success(result.getData().get(0));
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

    private String getPrimaryConnectId() {
        Class<?> entityClass = TableMeta.class;
        String tableName = Optional.ofNullable(entityClass.getAnnotation(Entity.class))
                .map(Entity::name)
                .filter(name -> !name.isEmpty())
                .orElseGet(entityClass::getSimpleName);
        EntityMeta entityMeta = MetaManager.singleInstance().getByEntityName(tableName);
        if (entityMeta == null || entityMeta.getTableMeta() == null || StringUtils.isBlank(entityMeta.getTableMeta().getConnectId())) {
            throw new RuntimeException("The model does not exist in memory");
        }
        return entityMeta.getTableMeta().getConnectId();
    }
}
