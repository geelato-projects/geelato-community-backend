package cn.geelato.core.mql.parser.keyword;

import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.filter.FilterGroup;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

import java.util.List;
import java.util.ArrayList;

@Getter
public enum DeleteKeyword implements KeyWordHandler {
    BRACKETS("@b") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            List<FilterGroup> childFilterGroup = parseKWBrackets(validator, jo, key);
            fg.setChildFilterGroup(childFilterGroup);
        }
    };

    private final String key;

    DeleteKeyword(String key) {
        this.key = key;
    }

    public static DeleteKeyword fromKey(String key) {
        for (DeleteKeyword k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        return null;
    }

    private static List<FilterGroup> parseKWBrackets(CommandValidator validator, JSONObject jo, String kw) {
        JSONArray bracketsJa = jo.getJSONArray(kw);
        List<FilterGroup> childFilterGroup = new ArrayList<>();
        if (bracketsJa != null) {
            bracketsJa.forEach(k -> {
                JSONObject bracket = (JSONObject) k;
                FilterGroup filterGroup = parseKWBracket(validator, bracket, kw);
                childFilterGroup.add(filterGroup);
            });
        }
        return childFilterGroup;
    }

    private static FilterGroup parseKWBracket(CommandValidator validator, JSONObject bracket, String kw) {
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
                        FilterGroup andChildGroup = parseKWBracket(validator, jsonObject, kw);
                        childFilterGroup.add(andChildGroup);
                    } else {
                        String[] ary = x.split("\\|");
                        String field = ary[0];
                        validator.validateField(field, "where");
                        if (ary.length == 1) {
                            filterGroup.addFilter(field, FilterGroup.Operator.eq, jsonObject.getString(x));
                        } else if (ary.length == 2) {
                            String fn = ary[1];
                            if (!FilterGroup.Operator.contains(fn)) {
                                validator.appendMessage(String.format("[%s]不支持%s,只支持%s", kw, fn, FilterGroup.Operator.getOperatorStrings()));
                            } else {
                                FilterGroup.Operator operator = FilterGroup.Operator.fromString(fn);
                                filterGroup.addFilter(field, operator, jsonObject.getString(x));
                            }
                        } else {
                            throw new cn.geelato.core.mql.parser.JsonParseException();
                        }
                    }
                });
            }
        }
        filterGroup.setChildFilterGroup(childFilterGroup);
        return filterGroup;
    }

}
