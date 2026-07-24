package cn.geelato.web.platform.srv.excel.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.datasource.annotation.UseDynamicDataSource;
import cn.geelato.meta.Dict;
import cn.geelato.meta.DictItem;
import cn.geelato.web.platform.srv.excel.entity.ComplexRuleData;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 复杂导入清洗规则处理器
 * <p>
 * 对 {@link ComplexExcelReader} 解析得到的扁平化结果应用清洗规则（{@link ComplexRuleData}）。
 * 规则按 order 升序执行；scope=TABLE 作用于顶层固定单元格，scope=LIST 作用于对应列表的行。
 * 规则类型语义与普通导入一致（{@link }）：DELETES/REPLACE/TRIM/UPPERCASE/
 * LOWERCASE/EXPRESSION/CHECKBOX/DICTIONARY/QUERYGOAL/QUERYRULE/SYM/MULTI。
 * <p>
 * 字典/模型查询在处理前一次性载入内存映射，不依赖Redis。
 *
 * @author diabl
 */
@Component
@Slf4j
public class ComplexRuleProcessor {

    @UseDynamicDataSource
    protected Dao dao;
    @Autowired
    protected RuleService ruleService;

    /**
     * 对扁平化解析结果应用清洗规则
     *
     * @param tableData 顶层固定单元格 {fieldName: value}
     * @param listData  列表数据 {listFieldName: [{fieldName: value}, ...]}
     * @param rules     清洗规则（已按 order 排序）
     * @return 处理后的 {tableData, listData}（listData 行数可能因 SYM/MULTI 膨胀而变化）
     */
    public Map<String, Object> process(Map<String, Object> tableData, Map<String, List<Map<String, Object>>> listData, List<ComplexRuleData> rules) {
        if (rules == null || rules.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("tableData", tableData);
            r.put("listData", listData);
            return r;
        }
        // 按 order 升序
        List<ComplexRuleData> sortedRules = rules.stream()
                .sorted((a, b) -> Integer.compare(a.getOrder() == null ? 0 : a.getOrder(), b.getOrder() == null ? 0 : b.getOrder()))
                .collect(Collectors.toList());
        // 预载字典/查询映射，避免逐行查库
        preloadLookups(sortedRules);

        for (ComplexRuleData rule : sortedRules) {
            if (rule.isScopeTable()) {
                applyTableRule(tableData, rule);
            } else if (rule.isScopeList()) {
                List<Map<String, Object>> rows = listData == null ? null : listData.get(rule.getListFieldName());
                if (rows != null && !rows.isEmpty()) {
                    List<Map<String, Object>> processed = applyListRule(rows, rule);
                    listData.put(rule.getListFieldName(), processed);
                }
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tableData", tableData);
        r.put("listData", listData);
        return r;
    }

    /**
     * 预载字典与模型查询映射
     * <p>
     * 字典按 dictCode、模型查询按 rule|goal 去重，整批一次性载入内存，
     * 后续每个单元格/行字段值处理时直接读缓存，绝不重复查库。
     * 同一个字典被表格单元格与列表字段同时引用时，只查询一次。
     */
    private void preloadLookups(List<ComplexRuleData> rules) {
        dictCache.clear();
        queryCache.clear();
        // 收集所有不重复的字典编码与查询键
        Set<String> dictCodes = new LinkedHashSet<>();
        Set<String> queryKeys = new LinkedHashSet<>();
        Map<String, ComplexRuleData> queryRuleMap = new LinkedHashMap<>();
        for (ComplexRuleData rule : rules) {
            if ((rule.isRuleTypeDictionary() || rule.isRuleTypeCheckBox()) && Strings.isNotBlank(rule.getRule())) {
                dictCodes.add(rule.getRule());
            } else if (rule.isRuleTypeQueryGoal() || rule.isRuleTypeQueryRule()) {
                String table = rule.getQueryRuleTable();
                List<String> columns = rule.getQueryRuleColumn();
                if (Strings.isNotBlank(table) && columns != null && !columns.isEmpty() && Strings.isNotBlank(rule.getGoal())) {
                    String key = rule.getRule() + "|" + rule.getGoal();
                    queryKeys.add(key);
                    queryRuleMap.putIfAbsent(key, rule);
                }
            }
        }
        // 字典：一次查询全部编码，按编码拆分映射
        if (!dictCodes.isEmpty()) {
            loadDictMaps(dictCodes);
        }
        // 模型查询：每个 rule|goal 一次查询
        for (String key : queryKeys) {
            ComplexRuleData rule = queryRuleMap.get(key);
            queryCache.put(key, loadQueryMap(rule.getQueryRuleTable(), rule.getGoal(), rule.getQueryRuleColumn()));
        }
    }

    private final Map<String, Map<String, String>> dictCache = new HashMap<>();
    private final Map<String, Map<String, Object>> queryCache = new HashMap<>();

    /**
     * 批量加载多个字典编码，按 dictCode 分组为 {itemName/itemNameEn -> itemCode}
     * <p>
     * 仅 2 次数据库查询（Dict、DictItem），结果按 dictCode 拆分存入 dictCache。
     *
     * @param dictCodes 字典编码集合
     */
    private void loadDictMaps(Set<String> dictCodes) {
        try {
            FilterGroup f = new FilterGroup().addFilter("dictCode", FilterGroup.Operator.in, String.join(",", dictCodes));
            List<Dict> dictList = dao.queryList(Dict.class, f, null);
            if (dictList == null || dictList.isEmpty()) {
                return;
            }
            // dictCode -> dictId
            Map<String, String> codeToId = new HashMap<>();
            for (Dict dict : dictList) {
                if (Strings.isNotBlank(dict.getDictCode())) {
                    codeToId.putIfAbsent(dict.getDictCode(), dict.getId());
                }
            }
            FilterGroup f1 = new FilterGroup().addFilter("dictId", FilterGroup.Operator.in, String.join(",", codeToId.values()));
            List<DictItem> items = dao.queryList(DictItem.class, f1, null);
            // 按 dictId 分组
            Map<String, List<DictItem>> itemsByDictId = new HashMap<>();
            if (items != null) {
                for (DictItem it : items) {
                    itemsByDictId.computeIfAbsent(it.getDictId(), k -> new ArrayList<>()).add(it);
                }
            }
            // 按 dictCode 构建映射
            for (Map.Entry<String, String> entry : codeToId.entrySet()) {
                Map<String, String> map = new LinkedHashMap<>();
                List<DictItem> list = itemsByDictId.get(entry.getValue());
                if (list != null) {
                    for (DictItem it : list) {
                        if (Strings.isNotBlank(it.getItemCode())) {
                            if (Strings.isNotBlank(it.getItemName())) {
                                map.putIfAbsent(it.getItemName(), it.getItemCode());
                            }
                            if (Strings.isNotBlank(it.getItemNameEn())) {
                                map.putIfAbsent(it.getItemNameEn(), it.getItemCode());
                            }
                        }
                    }
                }
                dictCache.put(entry.getKey(), map);
            }
        } catch (Exception ex) {
            log.error("batch load dicts {} failed: {}", dictCodes, ex.getMessage(), ex);
        }
    }

    /**
     * 模型查询：查询字段值 -> 目标字段值
     */
    private Map<String, Object> loadQueryMap(String tableName, String goal, List<String> columns) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            Set<String> cols = new LinkedHashSet<>();
            cols.add(goal);
            cols.addAll(columns);
            String ggl = String.format("{\"%s\": {\"@fs\": \"%s\"}}", tableName, String.join(",", cols));
            Map<String, Object> page = ruleService.queryForMapList(ggl, false);
            List<Map<String, Object>> data = getPageData(page);
            if (data != null) {
                for (Map<String, Object> row : data) {
                    Object goalValue = row.get(goal);
                    if (goalValue != null) {
                        for (String col : columns) {
                            Object v = row.get(col);
                            if (v != null) {
                                String key = String.valueOf(v);
                                if (Strings.isNotBlank(key)) {
                                    map.putIfAbsent(key, goalValue);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("load query [{}:{}] failed: {}", tableName, goal, ex.getMessage(), ex);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPageData(Map<String, Object> page) {
        if (page == null) {
            return Collections.emptyList();
        }
        Object data = page.get("data");
        return data instanceof List ? (List<Map<String, Object>>) data : Collections.emptyList();
    }

    /**
     * 对固定单元格应用规则（非多值分割类，TABLE 不支持行膨胀）
     */
    private void applyTableRule(Map<String, Object> tableData, ComplexRuleData rule) {
        String column = rule.getColumnName();
        if (Strings.isBlank(column)) {
            return;
        }
        Object cur = tableData.get(column);
        if (rule.isRuleTypeMultiValue()) {
            // 表格单元格多值仅取分割后第一个值（不膨胀行）
            Object v = applyMultiSingle(cur, rule);
            tableData.put(column, v);
        } else {
            tableData.put(column, transform(cur, rule, tableData));
        }
    }

    /**
     * 对列表行应用规则
     */
    private List<Map<String, Object>> applyListRule(List<Map<String, Object>> rows, ComplexRuleData rule) {
        String column = rule.getColumnName();
        if (Strings.isBlank(column)) {
            return rows;
        }
        if (rule.isRuleTypeMultiValue()) {
            // 多值分割可能膨胀行
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                List<Map<String, Object>> expanded = expandMultiRow(row, column, rule);
                out.addAll(expanded);
            }
            return out;
        }
        for (Map<String, Object> row : rows) {
            Object cur = row.get(column);
            row.put(column, transform(cur, rule, row));
        }
        return rows;
    }

    /**
     * 单值转换规则（DELETES/REPLACE/TRIM/CASE/EXPRESSION/CHECKBOX/DICTIONARY/QUERYGOAL/QUERYRULE）
     *
     * @param currentValue 当前值
     * @param rule         规则
     * @param valueMap     同层字段映射（用于 EXPRESSION 的 $.fieldName）
     * @return 处理后的值
     */
    private Object transform(Object currentValue, ComplexRuleData rule, Map<String, Object> valueMap) {
        String oldValue = currentValue == null ? null : String.valueOf(currentValue);
        Object newValue = null;
        if (rule.isRuleTypeDeletes()) {
            if (Strings.isNotBlank(rule.getRule())) {
                newValue = Strings.isNotBlank(oldValue) ? oldValue.replaceAll(rule.getRule(), "") : "";
                newValue = Strings.isNotBlank(String.valueOf(newValue)) ? newValue : null;
            }
        } else if (rule.isRuleTypeReplace()) {
            if (Strings.isNotBlank(rule.getRule()) && rule.getGoal() != null) {
                newValue = Strings.isNotBlank(oldValue) ? oldValue.replaceAll(rule.getRule(), rule.getGoal()) : "";
                newValue = Strings.isNotBlank(String.valueOf(newValue)) ? newValue : null;
            }
        } else if (rule.isRuleTypeUpperCase()) {
            newValue = Strings.isNotBlank(oldValue) ? oldValue.toUpperCase(Locale.ENGLISH) : null;
        } else if (rule.isRuleTypeLowerCase()) {
            newValue = Strings.isNotBlank(oldValue) ? oldValue.toLowerCase(Locale.ENGLISH) : null;
        } else if (rule.isRuleTypeTrim()) {
            newValue = (Strings.isNotBlank(oldValue) && Strings.isNotBlank(oldValue.trim())) ? oldValue.trim() : null;
        } else if (rule.isRuleTypeExpression()) {
            if (Strings.isNotBlank(rule.getRule())) {
                try {
                    newValue = JsProvider.executeExpression(rule.getRule(), valueMap);
                } catch (Exception ex) {
                    log.error("expression rule failed: {}", ex.getMessage(), ex);
                }
            }
        } else if (rule.isRuleTypeCheckBox()) {
            Map<String, String> dict = dictCache.get(rule.getRule());
            if (dict != null && Strings.isNotBlank(oldValue)) {
                String[] parts = oldValue.split(",");
                Set<String> nValues = new LinkedHashSet<>();
                for (String p : parts) {
                    String code = dict.get(p);
                    if (Strings.isNotBlank(code)) {
                        nValues.add(code);
                    }
                }
                newValue = nValues.isEmpty() ? null : String.join(",", nValues);
            }
        } else if (rule.isRuleTypeDictionary()) {
            Map<String, String> dict = dictCache.get(rule.getRule());
            if (dict != null && Strings.isNotBlank(oldValue)) {
                newValue = dict.get(oldValue);
            }
        } else if (rule.isRuleTypeQueryGoal() || rule.isRuleTypeQueryRule()) {
            String key = rule.getRule() + "|" + rule.getGoal();
            Map<String, Object> query = queryCache.get(key);
            if (query != null && Strings.isNotBlank(oldValue)) {
                newValue = query.get(oldValue);
            }
        }
        // 保留原值
        if (rule.isRetain() && newValue == null) {
            newValue = currentValue;
        }
        return newValue;
    }

    /**
     * 表格单元格多值：分割后取第一个非空
     */
    private Object applyMultiSingle(Object currentValue, ComplexRuleData rule) {
        if (Strings.isBlank(rule.getRule()) || currentValue == null) {
            return currentValue;
        }
        String[] parts = String.valueOf(currentValue).split(rule.getRule());
        for (String p : parts) {
            String t = p.trim();
            if (Strings.isNotBlank(t)) {
                return t;
            }
        }
        return rule.isRetain() ? currentValue : null;
    }

    /**
     * 列表行多值分割（SYM/MULTI），可能将一行扩展为多行
     */
    private List<Map<String, Object>> expandMultiRow(Map<String, Object> row, String column, ComplexRuleData rule) {
        Object cur = row.get(column);
        if (Strings.isBlank(rule.getRule()) || cur == null) {
            return Collections.singletonList(row);
        }
        String[] parts = String.valueOf(cur).split(rule.getRule());
        List<String> values = new ArrayList<>();
        for (String p : parts) {
            values.add(p.trim());
        }
        if (values.isEmpty() || (values.size() == 1 && Strings.isBlank(values.get(0)))) {
            return Collections.singletonList(row);
        }
        // 复杂模板单列多值分割：按值数量展开为多行，每行其余字段保持原值
        // goal=AB:CN 时不足位置取 null；否则取最后一个值
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            String v = values.get(i);
            if (Strings.isBlank(v)) {
                newRow.put(column, "AB:CN".equalsIgnoreCase(rule.getGoal()) ? null : (values.isEmpty() ? null : values.get(values.size() - 1)));
            } else {
                newRow.put(column, v);
            }
            out.add(newRow);
        }
        return out;
    }
}
