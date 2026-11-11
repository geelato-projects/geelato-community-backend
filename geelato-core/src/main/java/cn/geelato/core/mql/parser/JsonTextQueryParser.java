package cn.geelato.core.mql.parser;

import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.keyword.QueryKeyword;
import cn.geelato.core.mql.spi.support.MqlQueryFilterRuntimeResolver;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author geelato
 * 解析json字符串，并返回参数map
 */
@Slf4j
public class JsonTextQueryParser extends JsonTextParser {

    private final static String SUB_ENTITY_FLAG = "~";
    private final MetaManager metaManager = MetaManager.singleInstance();

    public static Map<String, String> orderMap;

    static {
        orderMap = new HashMap<>(2);
        orderMap.put("+", "asc");
        orderMap.put("-", "desc");
    }

    /**
     * 批量查询解析
     */
    public List<QueryCommand> parseMulti(String queryJsonText) {
        JSONArray ja = JSON.parseArray(queryJsonText);
        CommandValidator validator = new CommandValidator();
        List<QueryCommand> list = new ArrayList<>(ja.size());
        for (Object obj : ja) {
            JSONObject jo = (JSONObject) obj;
            if (jo.size() != 1) {
                validator.appendMessage("一个实体查询jsonText，有且只有一个根元素。");
                if (!validator.isSuccess()) {
                    logAndThrow(validator.getMessage(), "parseMulti validation failed");
                }
            }
            String key = jo.keySet().iterator().next();
            list.add(parse(key, jo.getJSONObject(key), validator));
        }
        return list;
    }

    /**
     * 单查询解析
     */
    public QueryCommand parse(String queryJsonText) {
        JSONObject jo = JSON.parseObject(queryJsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.size() != 1) {
            validator.appendMessage("一个实体查询jsonText，有且只有一个根元素。");
            if (!validator.isSuccess()) {
                logAndThrow(validator.getMessage(), "parse validation failed");
            }
        }
        String key = jo.keySet().iterator().next();
        return parse(key, jo.getJSONObject(key), validator);
    }

    private QueryCommand parse(String entityName, JSONObject jo, CommandValidator validator) {
        if (!validator.validateEntity(entityName)) {
            logAndThrow(validator.getMessage(), "validateEntity failed: {}", entityName);
        }
        // 物化视图实体只包含用户 SELECT 定义的列；查询构造端可能带入了标准审计字段（updateAt/delStatus 等）
        // 在解析前从 MQL JSON 中移除实体上不存在的字段引用（验证逻辑保持严格，不做绕过）
        if (metaManager.isMaterializedEntity(entityName)) {
            stripNonExistentFields(jo, validator.getEntityMeta(), entityName);
        }
        QueryCommand command = new QueryCommand();
        command.setEntityName(entityName);
        FilterGroup fg = new FilterGroup();
        command.setWhere(fg);

        jo.keySet().forEach(key -> {
            if (key.startsWith(KEYWORD_FLAG) && StringUtils.hasText(jo.getString(key))) {
                String value = jo.getString(key);
                QueryKeyword kw = QueryKeyword.fromKey(key);
                if (kw != null) {
                    kw.handle(jo, key, value, command, validator, fg, entityName);
                } else {
                    validator.appendMessage("[");
                    validator.appendMessage(key);
                    validator.appendMessage("]");
                    validator.appendMessage("不支持;");
                }
            } else if (key.startsWith(SUB_ENTITY_FLAG)) {
                command.getCommands().add(parse(key.substring(1), jo.getJSONObject(key), validator));
            } else {
                String[] ary = key.split(FILTER_FLAG);
                String field = ary[0];
                if (FunctionParser.isFunction((field))) {
                    field = new FunctionFieldValue(
                            FunctionParser.reconstruct(field, entityName)
                    ).getMysqlFunction();
                } else {
                    validator.validateField(field, "where");
                }
                if (ary.length == 1) {
                    fg.addFilter(field, FilterGroup.Operator.eq, jo.getString(key));
                } else if (ary.length == 2) {
                    String fn = ary[1];
                    if (!FilterGroup.Operator.contains(fn)) {
                        validator.appendMessage(String.format("[%s]不支持%s,只支持%s", key, fn, FilterGroup.Operator.getOperatorStrings()));
                    } else {
                        FilterGroup.Operator operator = FilterGroup.Operator.fromString(fn);
                        fg.addFilter(field, operator, jo.getString(key));
                    }
                } else {
                    throw new JsonParseException();
                }
            }
        });

        if (!validator.isSuccess()) {
            logAndThrow(validator.getMessage(), "final validation failed");
        }
        MqlQueryFilterRuntimeResolver.injectIfAvailable(command);
        return command;
    }

    /**
     * 物化视图实体预处理：从 MQL JSON 中移除实体上不存在的字段引用（@order 与过滤条件），
     * 使后续验证与 SQL 生成只处理实际存在的列。验证逻辑本身不放宽。
     */
    private void stripNonExistentFields(JSONObject jo, cn.geelato.core.meta.model.entity.EntityMeta entityMeta, String entityName) {
        // @order：字段不存在则移除排序
        String order = jo.getString("@order");
        if (StringUtils.hasText(order)) {
            String orderField = order.replaceFirst("^[+\\-\\s]+", "").split("\\s+")[0].trim();
            if (!entityMeta.containsField(orderField)) {
                jo.remove("@order");
                log.debug("物化视图实体 {} 不存在排序字段 {}，已移除", entityName, orderField);
            }
        }
        // 过滤条件：字段不存在则移除（跳过 @ 开头的关键字）
        jo.keySet().removeIf(key -> {
            if (key.startsWith(KEYWORD_FLAG) || key.startsWith(SUB_ENTITY_FLAG)) {
                return false;
            }
            String field = key.split(FILTER_FLAG)[0];
            return !entityMeta.containsField(field) && !"*".equals(field) && !"forceId".equals(field);
        });
    }
}
