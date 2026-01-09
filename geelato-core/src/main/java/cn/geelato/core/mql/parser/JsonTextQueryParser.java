package cn.geelato.core.mql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.keyword.QueryKeyword;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import cn.geelato.security.Permission;
import cn.geelato.security.PermissionRuleUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
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

    // page_num即offset，记录位置

    // 可对@fs中的字段进行重命名，字段原名+一到多个空格+字段重命名
    private final static String SUB_ENTITY_FLAG = "~";
    private final static String KW_HAVING = "@having";

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
                Assert.isTrue(validator.isSuccess(), validator.getMessage());
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
            Assert.isTrue(validator.isSuccess(), validator.getMessage());
        }
        String key = jo.keySet().iterator().next();
        return parse(key, jo.getJSONObject(key), validator);
    }

    private QueryCommand parse(String entityName, JSONObject jo, CommandValidator validator) {
        Assert.isTrue(validator.validateEntity(entityName), validator.getMessage());
        QueryCommand command = new QueryCommand();
        command.setEntityName(entityName);
        FilterGroup fg = new FilterGroup();
        fg.addFilter("tenantCode", SessionCtx.getCurrentTenantCode());

        if (SessionCtx.getCurrentUser().getDataPermissionByEntity(entityName) != null) {
            Permission dp = SessionCtx.getCurrentUser().getDataPermissionByEntity(entityName);
            String rule = PermissionRuleUtils.replaceRuleVariable(dp,SessionCtx.getCurrentUser());
            command.setOriginalWhere(rule);
        } else {
            command.setOriginalWhere(String.format("creator='%s'", SessionCtx.getCurrentUser().getUserId()));
        }
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

        Assert.isTrue(validator.isSuccess(), validator.getMessage());
        return command;
    }

}
