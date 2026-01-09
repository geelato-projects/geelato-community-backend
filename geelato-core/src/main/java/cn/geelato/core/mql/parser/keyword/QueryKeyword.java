package cn.geelato.core.mql.parser.keyword;

import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.command.CommandValidator;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.JsonTextQueryParser;
import cn.geelato.core.mql.parser.KeyWordHandler;
import cn.geelato.core.meta.model.parser.FunctionParser;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;
@SuppressWarnings("rawtypes")
public enum QueryKeyword implements KeyWordHandler {
    FIELDS("@fs") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            String[] segments = value.split(",(?![^()]*\\))");
            String[] fieldNames = new String[segments.length];
            List<String> foreigns = new ArrayList<>();
            java.util.regex.Pattern refPattern = java.util.regex.Pattern.compile("^ref\\(\\s*([A-Za-z_]\\w*(?:\\s*->\\s*[A-Za-z_]\\w*)?)\\s*\\)\\s+(\\S+)$");
            for (int i = 0; i < segments.length; i++) {
                if (FunctionParser.isFunction(segments[i])) {
                    fieldNames[i] = segments[i];
                } else {
                    String seg = segments[i].trim();
                    java.util.regex.Matcher m = refPattern.matcher(seg);
                    if (m.matches()) {
                        String foreignField = m.group(1).trim();
                        String alias = m.group(2).trim();
                        validator.validateField("ref(" + foreignField + ")", key);
                        fieldNames[i] = foreignField;
                        foreigns.add(foreignField);
                        if (command instanceof QueryCommand qc) {
                            qc.getAlias().put(foreignField, alias);
                        }
                    } else {
                        String[] ary = seg.split("\\s+");
                        if (ary.length == 1) {
                            validator.validateField(ary[0], key);
                            fieldNames[i] = ary[0];
                        } else if (ary.length == 2) {
                            validator.validateField(ary[0], key);
                            fieldNames[i] = ary[0];
                            if (command instanceof QueryCommand qc) {
                                qc.getAlias().put(ary[0], ary[1]);
                            }
                        } else {
                            validator.appendMessage(key);
                            validator.appendMessage("的值格式有误，正确如：name userName,age,sex，其中userName为重命名。");
                        }
                    }
                }
            }
            command.setFields(fieldNames);
            if (command instanceof QueryCommand qc && !foreigns.isEmpty()) {
                qc.setForeignFields(foreigns.toArray(new String[0]));
            }
        }
    },
    ORDER("@order") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            String[] segments = value.split(",(?![^()]*\\))");
            StringBuilder sb = new StringBuilder();
            for (String order : segments) {
                String[] splitCharacters = order.split("\\|");
                if (splitCharacters.length == 2 && JsonTextQueryParser.orderMap.containsKey(splitCharacters[1])) {
                    validator.validateField(splitCharacters[0], key);
                    sb.append(sb.isEmpty() ? "" : ",");
                    sb.append(validator.getColumnName(splitCharacters[0]));
                    sb.append(" ");
                    sb.append(JsonTextQueryParser.orderMap.get(splitCharacters[1]));
                } else {
                    validator.appendMessage(key);
                    validator.appendMessage("的值格式有误，正确如：age|+,name|-。");
                }
            }
            if (!sb.isEmpty()) {
                if (command instanceof QueryCommand qc) {
                    qc.setOrderBy(sb.toString());
                }
            }
        }
    },
    GROUP("@group") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            String[] segments = value.split(",(?![^()]*\\))");
            validator.validateField(segments, key);
            if (command instanceof QueryCommand qc) {
                qc.setGroupBy(value);
            }
        }
    },
    BRACKETS("@b") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            List<FilterGroup> childFilterGroup = parseKWBrackets(validator, jo,key);
            fg.setChildFilterGroup(childFilterGroup);
        }
    },
    PAGE("@p") {
        @Override
        public void handle(JSONObject jo, String key, String value, BaseCommand command, CommandValidator validator, FilterGroup fg, String entityName) {
            if (command instanceof QueryCommand qc) {
                qc.setQueryForList(true);
            }
            String[] page = value.split("[ ]*,[ ]*");
            boolean isSuccess = true;
            if (page.length == 2 && org.apache.commons.lang3.StringUtils.isNumeric(page[0]) && org.apache.commons.lang3.StringUtils.isNumeric(page[1])) {
                if (command instanceof QueryCommand qc) {
                    qc.setPageNum(Integer.parseInt(page[0]));
                    qc.setPageSize(Integer.parseInt(page[1]));
                    if (qc.getPageNum() <= 0 || qc.getPageSize() <= 0) {
                        isSuccess = false;
                    }
                } else {
                    isSuccess = false;
                }
            } else {
                isSuccess = false;
            }
            if (!isSuccess) {
                validator.appendMessage("[");
                validator.appendMessage(key);
                validator.appendMessage("]格式有误，正确格式为“第几页，每页记录数”，从第1页开始，如：1,10；");
            }
        }
    };

    private final String key;

    QueryKeyword(String key) {
        this.key = key;
    }

    public static QueryKeyword fromKey(String key) {
        for (QueryKeyword k : values()) {
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
