package cn.geelato.core.gql.parser;

import cn.geelato.core.Ctx;
import cn.geelato.utils.DateUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author geelato
 * 解析json字符串，并返回参数map
 */
public class JsonTextDeleteParser extends JsonTextParser {
    private static Logger logger = LoggerFactory.getLogger(JsonTextDeleteParser.class);

    private final static String KW_BIZ = "@biz";
    private final static String KEYWORD_FLAG = "@";
    private final static String FILTER_FLAG = "\\|";
    private final static String SUB_ENTITY_FLAG = "~";

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DateUtils.DATETIME);

    public DeleteCommand parse(String jsonText, Ctx ctx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        // TODO biz怎么用起来
        String biz = jo.getString(KW_BIZ);
        jo.remove(KW_BIZ);
        String key = jo.keySet().iterator().next();
        return parse(ctx, key, jo.getJSONObject(key), validator);
    }

    private DeleteCommand parse(Ctx ctx, String commandName, JSONObject jo, CommandValidator validator) {

        Assert.isTrue(validator.validateEntity(commandName), validator.getMessage());

        DeleteCommand command = new DeleteCommand();
        command.setEntityName(commandName);
        String newDataString = simpleDateFormat.format(new Date());
        FilterGroup fg = new FilterGroup();
        command.setWhere(fg);
        command.setCommandType(CommandType.Delete);
        Map<String, Object> params = new HashMap<>();
        if (validator.hasKeyField("delStatus")) {
            params.put("delStatus", 1);
        }
        if (validator.hasKeyField("deleteAt")) {
            params.put("deleteAt", newDataString);
        }
        if (validator.hasKeyField("updateAt")) {
            params.put("updateAt", newDataString);
        }
        if (validator.hasKeyField("updater")) {
            params.put("updater", ctx.get("userId"));
        }
        if (validator.hasKeyField("updaterName")) {
            params.put("updaterName", ctx.get("userName"));
        }
        String[] updateFields = new String[params.keySet().size()];
        params.keySet().toArray(updateFields);
        command.setFields(updateFields);
        command.setValueMap(params);
        jo.keySet().forEach(key -> {
            if (key.startsWith(KEYWORD_FLAG) && StringUtils.hasText(jo.getString(key))) {

            } else if (key.startsWith(SUB_ENTITY_FLAG)) {
                // 解析子实体
                command.getCommands().add(parse(ctx, key.substring(1), jo.getJSONObject(key), validator));
            } else {
                parseWhere(fg, key, jo, validator);
            }
        });

        Assert.isTrue(validator.isSuccess(), validator.getMessage());
        return command;
    }


    protected void parseWhere(FilterGroup fg, String key, JSONObject jo, CommandValidator validator) {
        // where子句过滤条件
        String[] ary = key.split(FILTER_FLAG);
        String field = ary[0];
        validator.validateField(field, "where");
        if (ary.length == 1) {
            fg.addFilter(field, FilterGroup.Operator.eq, jo.getString(key));
        } else if (ary.length == 2) {
            String fn = ary[1];
            if (!FilterGroup.Operator.contains(fn)) {
                validator.appendMessage("[");
                validator.appendMessage(key);
                validator.appendMessage("]");
                validator.appendMessage("不支持");
                validator.appendMessage(fn);
                validator.appendMessage(";只支持");
                validator.appendMessage(FilterGroup.Operator.getOperatorStrings());
            } else {
                FilterGroup.Operator operator = FilterGroup.Operator.fromString(fn);
                fg.addFilter(field, operator, jo.getString(key));
            }
        } else {
            // TODO 格式不对 throw
        }
    }
}
