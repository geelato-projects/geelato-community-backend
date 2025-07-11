package cn.geelato.core.gql.parser;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.command.CommandValidator;
import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import cn.geelato.security.Permission;
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
    private final static String KEYWORD_FLAG = "@";
    private final static String FILTER_FLAG = "\\|";
    // 可对@fs中的字段进行重命名，字段原名+一到多个空格+字段重命名
    private final static String ALIAS_FLAG = "\\s+";
    private final static String SUB_ENTITY_FLAG = "~";
    private final static String KW_PAGE = "@p";
    private final static String KW_FIELDS = "@fs";
    private final static String KW_ORDER_BY = "@order";
    private final static String KW_GROUP_BY = "@group";
    private final static String KW_BRACKETS = "@b";
    private final static String KW_HAVING = "@having";


    private static Map<String, String> orderMap = null;

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
            String rule = replaceRuleVariable(dp);
            command.setOriginalWhere(rule);
        } else {
            command.setOriginalWhere(String.format("creator='%s'", SessionCtx.getCurrentUser().getUserId()));
        }

        command.setWhere(fg);

        jo.keySet().forEach(key -> {
            if (key.startsWith(KEYWORD_FLAG) && StringUtils.hasText(jo.getString(key))) {
                String[] segments = jo.getString(key).split(",(?![^()]*\\))");
                switch (key) {
                    case KW_FIELDS:
                        String[] fieldNames = new String[segments.length];
                        for (int i = 0; i < segments.length; i++) {
                            if (FunctionParser.isFunction(segments[i])) {
                                fieldNames[i] = segments[i];
                            } else {
                                String[] ary = segments[i].split(ALIAS_FLAG);
                                if (ary.length == 1) {
                                    validator.validateField(ary[0], KW_FIELDS);
                                    fieldNames[i] = ary[0];
                                } else if (ary.length == 2) {
                                    validator.validateField(ary[0], KW_FIELDS);
                                    fieldNames[i] = ary[0];
                                    command.getAlias().put(ary[0], ary[1]);
                                } else {
                                    validator.appendMessage(KW_FIELDS);
                                    validator.appendMessage("的值格式有误，正确如：name userName,age,sex，其中userName为重命名。");
                                }
                            }
                        }
                        command.setFields(fieldNames);
                        break;
                    case KW_ORDER_BY:
                        StringBuilder sb = new StringBuilder();
                        for (String order : segments) {
                            String[] splitCharacters = order.split(FILTER_FLAG);
                            if (splitCharacters.length == 2 && orderMap.containsKey(splitCharacters[1])) {
                                validator.validateField(splitCharacters[0], KW_ORDER_BY);
                                sb.append(sb.isEmpty() ? "" : ",");
                                sb.append(validator.getColumnName(splitCharacters[0]));
                                sb.append(" ");
                                sb.append(orderMap.get(splitCharacters[1]));
                            } else {
                                validator.appendMessage(KW_ORDER_BY);
                                validator.appendMessage("的值格式有误，正确如：age|+,name|-。");
                            }
                        }
                        if (!sb.isEmpty()) {
                            command.setOrderBy(sb.toString());
                        }
                        break;
                    case KW_GROUP_BY:
                        validator.validateField(segments, KW_GROUP_BY);
                        command.setGroupBy(jo.getString(key));
                        break;
                    case KW_BRACKETS:
                        List<FilterGroup> childFilterGroup = parseKWBrackets(validator, jo);
                        fg.setChildFilterGroup(childFilterGroup);
                        break;
                    case KW_PAGE:
                        command.setQueryForList(true);
                        String[] page = jo.getString(KW_PAGE).split("[ ]*,[ ]*");
                        boolean isSuccess = true;
                        if (page.length == 2 && org.apache.commons.lang3.StringUtils.isNumeric(page[0]) && org.apache.commons.lang3.StringUtils.isNumeric(page[1])) {
                            command.setPageNum(Integer.parseInt(page[0]));
                            command.setPageSize(Integer.parseInt(page[1]));
                            if (command.getPageNum() <= 0 || command.getPageSize() <= 0) {
                                isSuccess = false;
                            }
                        } else {
                            isSuccess = false;
                        }
                        if (!isSuccess) {
                            validator.appendMessage("[");
                            validator.appendMessage(KW_PAGE);
                            validator.appendMessage("]格式有误，正确格式为“第几页，每页记录数”，从第1页开始，如：1,10；");
                        }
                        break;
                    default:
                        validator.appendMessage("[");
                        validator.appendMessage(key);
                        validator.appendMessage("]");
                        validator.appendMessage("不支持;");
                }
            } else if (key.startsWith(SUB_ENTITY_FLAG)) {
                // 解析子实体
                command.getCommands().add(parse(key.substring(1), jo.getJSONObject(key), validator));
            } else {
                // where子句过滤条件
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

    private String replaceRuleVariable(Permission dp) {
        return dp.getRule().replace("#currentUser.userId#", String.format("'%s'", SessionCtx.getCurrentUser().getUserId()))
                .replace("#currentUser.deptId#", String.format("'%s'", SessionCtx.getCurrentUser().getDefaultOrgId()))
                .replace("#currentUser.orgId#", String.format("'%s'", SessionCtx.getCurrentUser().getDefaultOrgId()))
                .replace("#currentUser.buId#", String.format("'%s'", SessionCtx.getCurrentUser().getBuId()))
                .replace("#currentUser.cooperatingOrgId#", String.format("'%s'", SessionCtx.getCurrentUser().getCooperatingOrgId()));
    }

    private List<FilterGroup> parseKWBrackets(CommandValidator validator, JSONObject jo) {
        JSONArray bracketsJa = jo.getJSONArray(KW_BRACKETS);
        List<FilterGroup> childFilterGroup = new ArrayList<>();
        bracketsJa.forEach(k -> {
            JSONObject bracket = (JSONObject) k;
            FilterGroup filterGroup = parseKWBracket(validator, bracket);
            childFilterGroup.add(filterGroup);
        });
        return childFilterGroup;
    }

    private FilterGroup parseKWBracket(CommandValidator validator, JSONObject bracket) {
        JSONArray ja = null;
        String currentLogic = "or";
        if (bracket.get("or") != null) {
            ja = (JSONArray) bracket.get("or");
        } else if (bracket.get("and") != null) {
            ja = (JSONArray) bracket.get("and");
            currentLogic = "and";
        }
        FilterGroup filterGroup = new FilterGroup(FilterGroup.Logic.fromString(currentLogic));
        List<FilterGroup> childFilterGroup = new ArrayList<>();
        if (ja != null) {
            for (Object o : ja) {
                JSONObject jsonObject = (JSONObject) o;
                jsonObject.keySet().forEach(x -> {
                    if ("and".equals(x) || "or".equals(x)) {
                        FilterGroup andChildGroup = parseKWBracket(validator, jsonObject);
                        childFilterGroup.add(andChildGroup);
                    } else {
                        String[] ary = x.split(FILTER_FLAG);
                        String field = ary[0];
                        validator.validateField(field, "where");
                        if (ary.length == 1) {
                            filterGroup.addFilter(field, FilterGroup.Operator.eq, jsonObject.getString(x));
                        } else if (ary.length == 2) {
                            String fn = ary[1];
                            if (!FilterGroup.Operator.contains(fn)) {
                                validator.appendMessage(String.format("[%s]不支持%s,只支持%s", KW_BRACKETS, fn, FilterGroup.Operator.getOperatorStrings()));
                            } else {
                                FilterGroup.Operator operator = FilterGroup.Operator.fromString(fn);
                                filterGroup.addFilter(field, operator, jsonObject.getString(x));
                            }
                        } else {
                            throw new JsonParseException();
                        }
                    }

                });
            }
        }
        filterGroup.setChildFilterGroup(childFilterGroup);
        return filterGroup;
    }

}
