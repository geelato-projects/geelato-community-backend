package cn.geelato.web.platform.m.ocr.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.plugin.ocr.PDFAnnotationPickContent;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfContent;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMeta;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMetaRule;
import cn.geelato.web.platform.m.ocr.enums.LocaleEnum;
import cn.geelato.web.platform.m.ocr.enums.MetaTypeEnum;
import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import cn.geelato.web.platform.m.ocr.enums.TimeUnitEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class OcrService extends BaseService {
    private final MetaManager metaManager = MetaManager.singleInstance();

    public List<OcrPdfContent> formatContent(List<PDFAnnotationPickContent> pdfAnnotationPickContents, List<OcrPdfMeta> ocrPdfMetas) throws ParseException {
        List<OcrPdfContent> pcList = OcrPdfContent.buildList(pdfAnnotationPickContents);
        if (ocrPdfMetas == null || ocrPdfMetas.isEmpty()) {
            return pcList;
        }
        Map<String, OcrPdfMeta> pmMap = OcrPdfMeta.toMap(ocrPdfMetas);
        for (OcrPdfContent pc : pcList) {
            // pdf,word 去读会自动带上换行符，这里去掉
            String content = removeLf(pc.getContent());
            // 如果没有配置规则 则直接返回内容
            if (!pmMap.containsKey(pc.getName())) {
                pc.setResult(content);
                continue;
            }
            // 如果有配置规则，则根据规则进行处理
            OcrPdfMeta pm = pmMap.get(pc.getName());
            List<OcrPdfMetaRule> rules = pm.toRules();
            if (content != null && rules != null && !rules.isEmpty()) {
                for (OcrPdfMetaRule rule : rules) {
                    if (RuleTypeEnum.TRIM.name().equalsIgnoreCase(rule.getType())) {
                        content = content.trim();
                    } else if (RuleTypeEnum.UPPERCASE.name().equalsIgnoreCase(rule.getType())) {
                        content = content.toUpperCase(Locale.ENGLISH);
                    } else if (RuleTypeEnum.LOWERCASE.name().equalsIgnoreCase(rule.getType())) {
                        content = content.toLowerCase(Locale.ENGLISH);
                    } else if (RuleTypeEnum.DELETES.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = content.replaceAll(rule.getRule(), "");
                        } else {
                            throw new RuntimeException("[Deletes] rule is empty");
                        }
                    } else if (RuleTypeEnum.REPLACE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            content = content.replaceAll(rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("[Replace] rule or goal is empty");
                        }
                    } else if (RuleTypeEnum.EXTRACT.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            Matcher matcher = Pattern.compile(rule.getRule()).matcher(content);
                            StringBuilder result = new StringBuilder();
                            while (matcher.find()) {
                                result.append(matcher.group());
                            }
                            content = result.toString();
                        } else {
                            throw new RuntimeException("[Extract] rule is empty");
                        }
                    } else if (RuleTypeEnum.PREFIX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = String.format("%s%s", rule.getRule(), content);
                        } else {
                            throw new RuntimeException("[Prefix] rule is empty");
                        }
                    } else if (RuleTypeEnum.SUFFIX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = String.format("%s%s", content, rule.getRule());
                        } else {
                            throw new RuntimeException("[Suffix] rule is empty");
                        }
                    } else if (RuleTypeEnum.CHECKBOX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = calculateItemCodes(content, rule.getRule());
                        } else {
                            throw new RuntimeException("[Checkbox] rule is empty");
                        }
                    } else if (RuleTypeEnum.DICTIONARY.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = calculateItemCode(content, rule.getRule());
                        } else {
                            throw new RuntimeException("[Checkbox] rule is empty");
                        }
                    } else if (RuleTypeEnum.QUERYGOAL.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            content = calculateTables(content, rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("[QueryGoal] rule or goal is empty");
                        }
                    } else if (RuleTypeEnum.QUERYRULE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            content = calculateTables(content, rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("[QueryGoal] rule or goal is empty");
                        }
                    } else if (RuleTypeEnum.TIMECONVERSION.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getLocale())) {
                            if (rule.getRule().indexOf("zzz") != -1 && Strings.isNotBlank(rule.getTimeZone())) {
                                content = convertTime(content, rule.getRule(), rule.getGoal(), rule.getLocale(), rule.getTimeZone());
                            } else {
                                throw new RuntimeException("[TimeConversion] timeZone is empty");
                            }
                        } else {
                            throw new RuntimeException("[TimeConversion] rule or goal or locale is empty");
                        }
                    } else if (RuleTypeEnum.TIMECHANGE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getExtra())) {
                            content = calculateTime(content, rule.getRule(), rule.getGoal(), rule.getExtra());
                        } else {
                            throw new RuntimeException("[TimeChange] rule or goal or extra is empty");
                        }
                    } else if (RuleTypeEnum.EXPRESSION.name().equalsIgnoreCase(rule.getType())) {
                        Map<String, Object> valueMap = OcrPdfContent.toMap(pcList);
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = JsProvider.executeExpression(rule.getRule(), valueMap).toString();
                        } else {
                            throw new RuntimeException("[Expression] rule is empty");
                        }
                    }
                }
            }
            // 数据类型处理
            pc.setResult(toFormat(content, pm.getType()));
        }

        return pcList;
    }

    /**
     * 将字符串转换为指定数据类型的对象
     *
     * @param str      待转换的字符串
     * @param dataType 目标数据类型，通过MetaTypeEnum枚举类表示
     * @return 转换后的对象，如果转换失败则返回null
     */
    private Object toFormat(String str, String dataType) {
        Object result = null;
        if (MetaTypeEnum.STRING.name().equalsIgnoreCase(dataType)) {
            result = str;
        } else if (MetaTypeEnum.NUMBER.name().equalsIgnoreCase(dataType)) {
            if (Strings.isBlank(str)) {
                result = 0;
            } else if (str.indexOf(".") == -1) {
                result = Long.parseLong(str);
            } else {
                result = new BigDecimal(str).doubleValue();
            }
        } else if (MetaTypeEnum.BOOLEAN.name().equalsIgnoreCase(dataType)) {
            if ("1".equalsIgnoreCase(str) || "true".equalsIgnoreCase(str)) {
                result = true;
            } else {
                result = false;
            }
        }
        return result;
    }

    /**
     * 移除字符串末尾的换行符（包括"\r\n"、"\n"或"\r"）
     *
     * @param str 待处理的字符串
     * @return 移除换行符后的字符串
     */
    private String removeLf(String str) {
        if (str != null) {
            if (str.endsWith("\r\n")) {
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("\n")) {
                str = str.substring(0, str.length() - 1);
            } else if (str.endsWith("\r")) {
                str = str.substring(0, str.length() - 1);
            }
        }

        return str;
    }

    /**
     * 将指定格式的时间字符串转换为另一种格式的时间字符串
     *
     * @param time     待转换的时间字符串
     * @param parse    原始时间字符串的格式
     * @param format   目标时间字符串的格式
     * @param timeZone 时间时区
     * @param locale   地区设置
     * @return 转换后的时间字符串
     * @throws ParseException 如果时间字符串的格式与指定的格式不匹配，则抛出此异常
     */
    private String convertTime(String time, String parse, String format, String timeZone, String locale) throws ParseException {
        Date date = convertTime(time, parse, timeZone, locale);
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * 将指定格式的字符串转换为Date对象
     *
     * @param time     待转换的时间字符串
     * @param parse    时间字符串的格式
     * @param timeZone 时间时区
     * @param locale   地区设置
     * @return 转换后的Date对象
     * @throws ParseException 如果时间字符串的格式与指定的格式不匹配，则抛出此异常
     */
    private Date convertTime(String time, String parse, String timeZone, String locale) throws ParseException {
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        Locale le = LocaleEnum.getDefaultLocale(locale);
        SimpleDateFormat sdf = new SimpleDateFormat(parse, le);
        sdf.setTimeZone(tz);
        return sdf.parse(time);
    }

    /**
     * 根据给定的时间、格式、数量和时间单位计算新的时间
     *
     * @param time   原始时间字符串
     * @param format 时间格式
     * @param amount 增减的时间数量
     * @param unit   时间单位
     * @return 计算后的时间字符串
     * @throws ParseException 如果解析时间字符串时发生错误，则抛出此异常
     */
    private String calculateTime(String time, String format, String amount, String unit) throws ParseException {
        Date date = new SimpleDateFormat(format).parse(time);
        Calendar calendar = Calendar.getInstance();
        int unitValue = TimeUnitEnum.getValueByName(unit);
        calendar.setTime(date);
        if (unitValue > -1 && amount.matches("^-?\\d+$")) {
            calendar.add(unitValue, Integer.parseInt(amount));
        }
        return new SimpleDateFormat(format).format(calendar.getTime());
    }

    /**
     * 根据字典编码查询字典项列表
     *
     * @param dictCode 字典编码
     * @return 返回与给定字典编码匹配的字典项列表，如果未找到匹配的字典项，则返回null
     */
    private List<DictItem> queryDictItemsByDictCode(String dictCode) {
        FilterGroup filter1 = new FilterGroup();
        filter1.addFilter("dictCode", FilterGroup.Operator.in, dictCode);
        filter1.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));
        List<Dict> dicts = dao.queryList(Dict.class, filter1, "updatedAt desc");
        if (dicts != null && !dicts.isEmpty()) {
            FilterGroup filter2 = new FilterGroup();
            filter2.addFilter("dictId", FilterGroup.Operator.in, dicts.get(0).getId());
            filter2.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));
            return dao.queryList(DictItem.class, filter2, null);
        }
        return null;
    }

    /**
     * 根据字典编码和字典项名称计算字典项编码
     *
     * @param itemName 字典项名称
     * @param dictCode 字典编码
     * @return 如果找到匹配的字典项，则返回其字典项编码；否则返回null
     */
    private String calculateItemCode(String itemName, String dictCode) {
        String itemCode = null;
        // 获取字典项
        List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
        // 比对
        if (dictItemList != null && dictItemList.size() > 0) {
            for (DictItem dictItem : dictItemList) {
                if (Strings.isNotBlank(itemName) && itemName.equals(dictItem.getItemName())) {
                    itemCode = dictItem.getItemCode();
                    break;
                }
            }
        }
        return itemCode;
    }

    /**
     * 根据字典编码和多个字典项名称计算对应的字典项编码
     *
     * @param itemNames 字典项名称列表，以逗号分隔
     * @param dictCode  字典编码
     * @return 如果找到匹配的字典项，则返回其字典项编码列表，以逗号分隔；否则返回null
     */
    private String calculateItemCodes(String itemNames, String dictCode) {
        List<String> names = stringToList(itemNames, ",");
        if (names == null || names.size() == 0) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        // 获取字典项
        List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
        // 比对
        if (dictItemList != null && dictItemList.size() > 0) {
            for (DictItem dictItem : dictItemList) {
                if (names.contains(dictItem.getItemName())) {
                    codes.add(dictItem.getItemCode());
                }
            }
        }
        return codes.size() > 0 ? String.join(",", codes) : null;
    }

    /**
     * 根据给定的规则和目标列名，从数据库中查询并返回目标列的值
     *
     * @param value 字段值
     * @param rule  规则字符串，格式为"表名:列名1,列名2..."
     * @param goal  目标列名
     * @return 查询结果，如果查询成功则返回目标列的值，否则返回null
     */
    private String calculateTables(String value, String rule, String goal) {
        Map<String, Object> map = parseRuleMap(rule, goal);
        if (map == null) {
            return null;
        }
        String tableName = map.get("tableName") == null ? "" : map.get("tableName").toString();
        List<String> columnNames = map.get("query") == null ? null : (List<String>) map.get("query");
        String goalColumnName = map.get("goal") == null ? null : map.get("goal").toString();
        if (Strings.isBlank(tableName) || columnNames == null || columnNames.isEmpty() || goalColumnName == null) {
            return null;
        }
        boolean hasDel = map.get("hasDel") != null && Boolean.parseBoolean(map.get("hasDel").toString());

        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(goalColumnName).append(" from ").append(tableName).append(" where ");
        if (hasDel) {
            sql.append(" del_status = 0 and ");
        }
        sql.append("(");
        for (int i = 0; i < columnNames.size(); i++) {
            sql.append(columnNames.get(i)).append(" = '").append(value).append("'");
            if (i != columnNames.size() - 1) {
                sql.append(" or ");
            }
        }
        sql.append(")");
        try {
            List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(sql.toString());
            if (list != null && list.size() > 0) {
                Object obj = list.get(0).get(goalColumnName);
                return obj != null ? obj.toString() : null;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    /**
     * 解析规则字符串并生成包含规则信息的映射表
     *
     * @param rule 规则字符串，格式为"表名:列名1,列名2..."
     * @param goal 目标列名
     * @return 包含规则信息的映射表，如果解析失败则返回null
     */
    private Map<String, Object> parseRuleMap(String rule, String goal) {
        // 解析规则
        Map<String, Object> map = new HashMap<>();
        String tableName = null;
        List<String> columnNames = new ArrayList<>();
        if (rule.contains(":")) {
            String[] arr = rule.split(":");
            if (arr.length == 2) {
                tableName = arr[0];
                columnNames = stringToList(arr[1], ",");
            }
        }
        if (Strings.isBlank(tableName) || columnNames == null || columnNames.size() == 0) {
            return null;
        }
        // 校验表名
        EntityMeta entityMeta = metaManager.getByEntityName(tableName);
        if (entityMeta == null) {
            return null;
        }
        map.put("tableName", tableName);
        // 校验列名
        Map<String, ColumnMeta> columnMap = getColumnMap(entityMeta);
        if (!columnMap.containsKey(goal)) {
            return null;
        }
        map.put("goal", columnMap.get(goal).getName());
        // 校验列名
        List<String> querys = new ArrayList<>();
        for (String columnName : columnNames) {
            if (!columnMap.containsKey(columnName)) {
                return null;
            }
            querys.add(columnMap.get(columnName).getName());
        }
        map.put("query", querys);
        map.put("hasDel", columnMap.containsKey("delStatus"));
        return map;
    }


    /**
     * 根据实体元数据生成列名映射
     *
     * @param entityMeta 实体元数据对象
     * @return 包含字段名和列元数据的映射表
     */
    private Map<String, ColumnMeta> getColumnMap(EntityMeta entityMeta) {
        Collection<FieldMeta> fieldMetas = entityMeta.getFieldMetas();
        Map<String, ColumnMeta> columnMap = new HashMap<>();
        for (FieldMeta fieldMeta : fieldMetas) {
            if (fieldMeta.getColumnMeta() != null) {
                columnMap.put(fieldMeta.getFieldName(), fieldMeta.getColumnMeta());
            }
        }
        return columnMap;
    }

    /**
     * 将以指定分隔符分隔的字符串转换为字符串列表
     *
     * @param arrString 待转换的字符串
     * @param split     分隔符
     * @return 转换后的字符串列表，如果输入字符串为空或分割后没有有效项，则返回空列表
     */
    private List<String> stringToList(String arrString, String split) {
        List<String> list = new ArrayList<>();
        if (arrString != null) {
            String[] arr = arrString.split(split);
            if (arr != null) {
                for (String item : arr) {
                    if (Strings.isNotBlank(item)) {
                        list.add(item);
                    }
                }
            }
        }
        return list;
    }
}























