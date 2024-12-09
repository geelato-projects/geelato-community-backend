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
import cn.geelato.plugin.ocr.PDFResolveData;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfContent;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMeta;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMetaRule;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfWhole;
import cn.geelato.web.platform.m.ocr.enums.MetaTypeEnum;
import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

@Component
@Slf4j
public class OcrService extends BaseService {
    private final MetaManager metaManager = MetaManager.singleInstance();

    /**
     * 数据处理
     *
     * @param pdfResolveData PDF解析数据
     * @param ocrPdfMetas    ocr元数据
     * @return 处理后的数据
     * @throws ParseException 如果解析规则时发生错误
     */
    public OcrPdfWhole formatContent(PDFResolveData pdfResolveData, List<OcrPdfMeta> ocrPdfMetas) throws ParseException {
        OcrPdfWhole ocrPdfWhole = new OcrPdfWhole();
        List<PDFAnnotationPickContent> pdfAnnotationPickContents = new ArrayList<>();
        if (pdfResolveData == null) {
            pdfAnnotationPickContents = pdfResolveData.getPdfAnnotationPickContentList();
            ocrPdfWhole.setWholeContent(pdfResolveData.getWholeContent());
        }
        List<OcrPdfContent> ocrPdfContents = formatContent(pdfAnnotationPickContents, ocrPdfMetas);
        ocrPdfWhole.setOcrPdfContents(ocrPdfContents);
        return ocrPdfWhole;
    }

    /**
     * 数据处理
     *
     * @param pdfAnnotationPickContents PDF解析数据
     * @param ocrPdfMetas               ocr元数据
     * @return 处理后的数据
     * @throws ParseException 如果解析规则时发生错误
     */
    public List<OcrPdfContent> formatContent(List<PDFAnnotationPickContent> pdfAnnotationPickContents, List<OcrPdfMeta> ocrPdfMetas) {
        List<OcrPdfContent> ocrPdfContents = OcrPdfContent.buildList(pdfAnnotationPickContents);
        if (ocrPdfContents == null || ocrPdfContents.isEmpty()) {
            return ocrPdfContents;
        }
        if (ocrPdfMetas == null || ocrPdfMetas.isEmpty()) {
            return ocrPdfContents;
        }
        Map<String, OcrPdfMeta> pmMap = OcrPdfMeta.toMap(ocrPdfMetas);
        // 初始化规则
        for (OcrPdfContent pc : ocrPdfContents) {
            String content = initRules(pc.getContent());
            pc.setResult(content);
        }
        Map<String, Object> opcMap = OcrPdfContent.toMap(ocrPdfContents);
        // 如果有配置规则，则根据规则进行处理
        for (OcrPdfContent pc : ocrPdfContents) {
            // 如果没有配置规则 则直接返回内容
            if (!pmMap.containsKey(pc.getName())) {
                continue;
            }
            // 如果有配置规则，则根据规则进行处理
            OcrPdfMeta pm = pmMap.get(pc.getName());
            List<OcrPdfMetaRule> rules = pm.toRules();
            try {
                String content = pc.getResult() == null ? pc.getContent() : pc.getResult().toString();
                // 数据处理
                content = handleRules(content, rules, opcMap);
                // 数据类型处理
                pc.setResult(toFormat(content, pm.getType()));
            } catch (Exception e) {
                // String errorMsg = String.format("解析 %s 的内容【%s】出错，%s。", pc.getName(), content, e.getMessage());
                pc.setErrorMsg(e.getMessage());
            }
        }

        return ocrPdfContents;
    }

    /**
     * 根据给定的内容、规则和OCR PDF内容列表，执行规则测试并返回处理后的内容。
     *
     * @param content        待处理的内容字符串。
     * @param rules          规则列表，用于处理content。
     * @param ocrPdfContents OCR PDF内容列表，用于初始化规则。
     * @return 处理后的内容字符串。
     * @throws RuntimeException 如果解析内容过程中出现错误，将抛出运行时异常。
     */
    public String ruleTest(String content, List<OcrPdfMetaRule> rules, List<OcrPdfContent> ocrPdfContents) {
        try {
            // 初始化规则
            Map<String, Object> opcMap = new HashMap<>();
            if (ocrPdfContents != null && !ocrPdfContents.isEmpty()) {
                for (OcrPdfContent pc : ocrPdfContents) {
                    String result = initRules(pc.getContent());
                    pc.setResult(result);
                }
                opcMap = OcrPdfContent.toMap(ocrPdfContents);
            }
            // 数据处理
            content = initRules(content);
            content = handleRules(content, rules, opcMap);
        } catch (Exception e) {
            throw new RuntimeException(String.format("解析内容【%s】出错，%s。", content, e.getMessage()), e);
        }
        return content;
    }

    /**
     * 初始化规则
     *
     * @param content 传入的内容
     * @return 初始化后的内容
     */
    private String initRules(String content) {
        // pdf,word 去读会自动带上换行符，这里去掉
        content = OcrUtils.removeLf(content);
        // 去掉前后空格
        if (Strings.isNotBlank(content)) {
            content = content.trim();
        }
        return content;
    }

    /**
     * 根据给定的规则和OCR PDF内容列表处理输入内容。
     *
     * @param content 待处理的内容字符串
     * @param rules   处理规则列表
     * @param opcMap  OCR PDF内容列表
     * @return 处理后的内容字符串
     */
    public String handleRules(String content, List<OcrPdfMetaRule> rules, Map<String, Object> opcMap) {
        // 规则处理
        if (content != null && rules != null && !rules.isEmpty()) {
            for (OcrPdfMetaRule rule : rules) {
                String ruleLabel = RuleTypeEnum.getLabelByValue(rule.getType());
                try {
                    // 循环处理中，如果内容为空，且规则的传入值不能为空，则跳过
                    if (content == null && RuleTypeEnum.isNotNull(rule.getType())) {
                        continue;
                    }
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
                            throw new RuntimeException("Regular expression is empty");
                        }
                    } else if (RuleTypeEnum.REPLACE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && rule.getGoal() != null) {
                            content = content.replaceAll(rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("Regular expression or replace is empty");
                        }
                    } else if (RuleTypeEnum.EXTRACT.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = OcrUtils.extract(content, rule.getRule());
                        } else {
                            throw new RuntimeException("Regular expression is empty");
                        }
                    } else if (RuleTypeEnum.CONSTANT.name().equalsIgnoreCase(rule.getType())) {
                        content = rule.getRule();
                    } else if (RuleTypeEnum.PREFIX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = String.format("%s%s", rule.getRule(), content);
                        } else {
                            throw new RuntimeException("Prefix is empty");
                        }
                    } else if (RuleTypeEnum.SUFFIX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = String.format("%s%s", content, rule.getRule());
                        } else {
                            throw new RuntimeException("Suffix is empty");
                        }
                    } else if (RuleTypeEnum.CHECKBOX.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = calculateItemCodes(content, rule.getRule());
                        } else {
                            throw new RuntimeException("Dictionary encoding is empty");
                        }
                    } else if (RuleTypeEnum.DICTIONARY.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = calculateItemCode(content, rule.getRule());
                        } else {
                            throw new RuntimeException("Dictionary encoding is empty");
                        }
                    } else if (RuleTypeEnum.QUERYGOAL.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            content = calculateTables(content, rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("Entity or column is empty");
                        }
                    } else if (RuleTypeEnum.QUERYRULE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            content = calculateTables(content, rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("Entity or column is empty");
                        }
                    } else if (RuleTypeEnum.TIMECONVERSION.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getLocale())) {
                            content = OcrUtils.convertTime(content, rule.getRule(), rule.getGoal(), rule.getTimeZone(), rule.getLocale());
                        } else {
                            throw new RuntimeException("TimeFormat or TimeParse or locale is empty");
                        }
                    } else if (RuleTypeEnum.TIMECHANGE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getExtra())) {
                            content = OcrUtils.calculateTime(content, rule.getExtra(), rule.getRule(), rule.getGoal());
                        } else {
                            throw new RuntimeException("Amount or unit or timeParse is empty");
                        }
                    } else if (RuleTypeEnum.EXPRESSION.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            Object jsResult = JsProvider.executeExpression(rule.getRule(), opcMap);
                            content = jsResult != null ? jsResult.toString() : null;
                        } else {
                            throw new RuntimeException("Javascript expression is empty");
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(String.format("[%s](%s)", Strings.isNotBlank(ruleLabel) ? ruleLabel : rule.getType(), e.getMessage()), e);
                }
            }
        }
        return content;
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
                if (Long.parseLong(str) < Integer.MAX_VALUE) {
                    result = Integer.parseInt(str);
                } else {
                    result = Long.parseLong(str);
                }
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
     * 根据字典编码查询字典项列表
     *
     * @param dictCode 字典编码
     * @return 返回与给定字典编码匹配的字典项列表，如果未找到匹配的字典项，则返回null
     */
    private List<DictItem> queryDictItemsByDictCode(String dictCode) {
        FilterGroup filter1 = new FilterGroup();
        filter1.addFilter("dictCode", FilterGroup.Operator.in, dictCode);
        filter1.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));
        List<Dict> dicts = dao.queryList(Dict.class, filter1, "update_at DESC");
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
        List<String> names = OcrUtils.stringToList(itemNames);
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
                columnNames = OcrUtils.stringToListDr(arr[1]);
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
}























