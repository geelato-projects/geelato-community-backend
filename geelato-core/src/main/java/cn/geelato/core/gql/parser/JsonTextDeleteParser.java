package cn.geelato.core.gql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.command.CommandType;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.gql.command.DeleteCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.keyword.DeleteKeyword;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author geelato
 * 解析json字符串，并返回参数map
 */
@Slf4j
public class JsonTextDeleteParser extends JsonTextParser {

    private final static String KW_BIZ = "@biz";
    private final static String SUB_ENTITY_FLAG = "~";

    public DeleteCommand parse(String jsonText, SessionCtx sessionCtx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        // TODO biz怎么用起来
        String biz = jo.getString(KW_BIZ);
        jo.remove(KW_BIZ);
        String key = jo.keySet().iterator().next();
        return parse(sessionCtx, key, jo.getJSONObject(key), validator);
    }

    private DeleteCommand parse(SessionCtx sessionCtx, String commandName, JSONObject jo, CommandValidator validator) {

        Assert.isTrue(validator.validateEntity(commandName), validator.getMessage());

        DeleteCommand command = new DeleteCommand();
        command.setEntityName(commandName);

        FilterGroup fg = new FilterGroup();
        command.setWhere(fg);
        command.setCommandType(CommandType.Delete);
        Map<String, Object> params = new HashMap<>();
        putDeleteDefaultField(sessionCtx,params,validator);

        String[] updateFields = new String[params.size()];
        params.keySet().toArray(updateFields);
        command.setFields(updateFields);
        command.setValueMap(params);
        jo.keySet().forEach(key -> {
            if (key.startsWith(KEYWORD_FLAG) && StringUtils.hasText(jo.getString(key))) {
                String value = jo.getString(key);
                DeleteKeyword kw = DeleteKeyword.fromKey(key);
                if (kw != null) {
                    kw.handle(jo, key, value, command, validator, fg, commandName);
                } else {
                    validator.appendMessage("[");
                    validator.appendMessage(key);
                    validator.appendMessage("]");
                    validator.appendMessage("不支持;");
                }
            } else if (key.startsWith(SUB_ENTITY_FLAG)) {
                // 解析子实体
                command.getCommands().add(parse(sessionCtx, key.substring(1), jo.getJSONObject(key), validator));
            } else {
                parseWhere(fg, key, jo, validator);
            }
        });

        Assert.isTrue(validator.isSuccess(), validator.getMessage());
        return command;
    }

    private void putDeleteDefaultField(SessionCtx sessionCtx, Map<String, Object> params, CommandValidator validator) {
        String newDataString = simpleDateFormat.format(new Date());
        if (validator.hasKeyField("delStatus")) {
            params.put("delStatus", 1);
        }
        if (validator.hasKeyField("deleteAt")) {
            params.put("deleteAt", newDataString);
        }
        putBaseDefaultField(sessionCtx,params,validator);
    }




    protected void parseWhere(FilterGroup fg, String key, JSONObject jo, CommandValidator validator) {
        String[] ary = key.split(FILTER_FLAG);
        String field = ary[0];
        validator.validateField(field, "where");
        if (ary.length == 1) {
            fg.addFilter(field, FilterGroup.Operator.eq, jo.getString(key));
        } else if (ary.length == 2) {
            String fn = ary[1];
            if (!FilterGroup.Operator.contains(fn)) {
                validator.appendMessage(String.format("[%s]不支持%s,只支持%s",key,fn,FilterGroup.Operator.getOperatorStrings()));
            } else {
                FilterGroup.Operator operator = FilterGroup.Operator.fromString(fn);
                fg.addFilter(field, operator, jo.getString(key));
            }
        } else {
            throw new JsonParseException();
        }
    }
}
