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
import cn.geelato.web.platform.m.ocr.entity.*;
import cn.geelato.web.platform.m.ocr.enums.MetaTypeEnum;
import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        if (pdfResolveData != null) {
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
    public String initRules(String content) {
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
                        if (Strings.isNotBlank(rule.getRule())) {
                            content = content.replaceAll(rule.getRule(), rule.getGoal() == null ? "" : rule.getGoal());
                        } else {
                            throw new RuntimeException("Regular expression is empty");
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
                            String result = calculateItemCodes(content, rule.getRule(), rule.getGoal());
                            content = rule.isRetain() && Strings.isBlank(result) ? content : result;
                        } else {
                            throw new RuntimeException("Dictionary encoding is empty");
                        }
                    } else if (RuleTypeEnum.DICTIONARY.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            String result = calculateItemCode(content, rule.getRule(), rule.getGoal());
                            content = rule.isRetain() && Strings.isBlank(result) ? content : result;
                        } else {
                            throw new RuntimeException("Dictionary encoding is empty");
                        }
                    } else if (RuleTypeEnum.RADIO.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule())) {
                            String result = calculateRadio(content, rule.getRule(), rule.getGoal());
                            content = rule.isRetain() && Strings.isBlank(result) ? content : result;
                        } else {
                            throw new RuntimeException("Dictionary encoding is empty");
                        }
                    } else if (RuleTypeEnum.QUERYGOAL.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            String result = calculateTables(content, rule.getRule(), rule.getGoal());
                            content = rule.isRetain() && Strings.isBlank(result) ? content : result;
                        } else {
                            throw new RuntimeException("Entity or column is empty");
                        }
                    } else if (RuleTypeEnum.QUERYRULE.name().equalsIgnoreCase(rule.getType())) {
                        if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                            String result = calculateTables(content, rule.getRule(), rule.getGoal());
                            content = rule.isRetain() && Strings.isBlank(result) ? content : result;
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
     * 验证模板名称是否有效
     *
     * @param ocrPdfRule     OCR PDF规则对象
     * @param pdfResolveData PDF解析数据对象
     * @return 如果模板名称有效，则返回true；否则返回false
     */
    public boolean validateTemplateName(OcrPdfRule ocrPdfRule, PDFResolveData pdfResolveData) {
        if (pdfResolveData == null) {
            return false;
        }
        return validateTemplateName(ocrPdfRule, pdfResolveData.getPdfAnnotationPickContentList());
    }

    /**
     * 验证模板名称是否有效
     *
     * @param ocrPdfRule                   OCR PDF规则对象
     * @param pdfAnnotationPickContentList PDF注解选择内容列表
     * @return 如果模板名称有效，则返回true；否则返回false
     */
    public boolean validateTemplateName(OcrPdfRule ocrPdfRule, List<PDFAnnotationPickContent> pdfAnnotationPickContentList) {
        if (pdfAnnotationPickContentList == null || pdfAnnotationPickContentList.isEmpty()) {
            return false;
        }
        if (ocrPdfRule == null || ocrPdfRule.getName() == null || ocrPdfRule.getName().length == 0) {
            return true;
        }
        List<String> templateNames = pdfAnnotationPickContentList.stream().map(PDFAnnotationPickContent::getAnnotationAreaContent).distinct().collect(Collectors.toList());
        for (String name : ocrPdfRule.getName()) {
            if (Strings.isNotBlank(name) && !templateNames.contains(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证PDF文件中的模板正则表达式是否与给定的规则匹配
     *
     * @param ocrPdfRule     PDF模板规则对象
     * @param pdfResolveData PDF文件整体对象
     * @return 如果PDF文件中的模板与给定的规则匹配，则返回true；否则返回false
     */
    public boolean validateTemplateRegExp1(OcrPdfRule ocrPdfRule, PDFResolveData pdfResolveData) {
        if (pdfResolveData == null) {
            return false;
        }
        return validateTemplateRegExp1(ocrPdfRule, pdfResolveData.getWholeContent(), pdfResolveData.getPdfAnnotationPickContentList());
    }

    /**
     * 验证PDF模板的正则表达式是否与给定的PDF内容和内容列表匹配
     *
     * @param ocrPdfRule                   PDF模板规则对象
     * @param wholeContent                 PDF文件整体内容
     * @param pdfAnnotationPickContentList PDF内容列表
     * @return 如果PDF模板的正则表达式与给定的PDF内容和内容列表匹配，则返回true；否则返回false
     */
    public boolean validateTemplateRegExp1(OcrPdfRule ocrPdfRule, String wholeContent, List<PDFAnnotationPickContent> pdfAnnotationPickContentList) {
        if (Strings.isBlank(wholeContent) || pdfAnnotationPickContentList == null || pdfAnnotationPickContentList.isEmpty()) {
            return false;
        }
        if (ocrPdfRule == null || ocrPdfRule.getRegexp() == null || ocrPdfRule.getRegexp().isEmpty()) {
            return true;
        }
        // 并将PDF内容列表转换为Map对象
        Map<String, String> resultMap = new HashMap<>();
        for (PDFAnnotationPickContent pdfAnnotationPickContent : pdfAnnotationPickContentList) {
            String key = pdfAnnotationPickContent.getAnnotationAreaContent();
            String value = initRules(pdfAnnotationPickContent.getInstanceAreaContent());
            if (Strings.isNotBlank(key) && Strings.isNotBlank(value)) {
                resultMap.put(key, value);
            }
        }
        for (Map.Entry<String, String> entry : ocrPdfRule.getRegexp().entrySet()) {
            if (Strings.isNotBlank(entry.getKey()) && Strings.isNotBlank(entry.getValue())) {
                // 此处使用ALL关键字匹配整个PDF内容，而非单个OCR PDF内容的匹配
                String content = "ALL".equalsIgnoreCase(entry.getKey()) ? wholeContent : resultMap.get(entry.getKey());
                // 正则匹配，如果匹配失败则返回false
                if (Strings.isBlank(content) || !Pattern.compile(entry.getValue()).matcher(content).find()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 验证PDF文件中的模板正则表达式是否与给定的规则匹配
     *
     * @param ocrPdfRule  PDF模板规则对象
     * @param ocrPdfWhole PDF文件整体对象
     * @return 如果PDF文件中的模板与给定的规则匹配，则返回true；否则返回false
     */
    public boolean validateTemplateRegExp(OcrPdfRule ocrPdfRule, OcrPdfWhole ocrPdfWhole) {
        if (ocrPdfWhole == null) {
            return false;
        }
        return validateTemplateRegExp(ocrPdfRule, ocrPdfWhole.getWholeContent(), ocrPdfWhole.getOcrPdfContents());
    }

    /**
     * 验证PDF模板的正则表达式是否与给定的PDF内容和内容列表匹配
     *
     * @param ocrPdfRule        PDF模板规则对象
     * @param wholeContent      PDF文件整体内容
     * @param ocrPdfContentList PDF内容列表
     * @return 如果PDF模板的正则表达式与给定的PDF内容和内容列表匹配，则返回true；否则返回false
     */
    public boolean validateTemplateRegExp(OcrPdfRule ocrPdfRule, String wholeContent, List<OcrPdfContent> ocrPdfContentList) {
        if (Strings.isBlank(wholeContent) || ocrPdfContentList == null || ocrPdfContentList.isEmpty()) {
            return false;
        }
        if (ocrPdfRule == null || ocrPdfRule.getRegexp() == null || ocrPdfRule.getRegexp().isEmpty()) {
            return true;
        }
        // 并将PDF内容列表转换为Map对象
        Map<String, Object> resultMap = OcrPdfContent.toMap(ocrPdfContentList);
        for (Map.Entry<String, String> entry : ocrPdfRule.getRegexp().entrySet()) {
            if (Strings.isNotBlank(entry.getKey()) && Strings.isNotBlank(entry.getValue())) {
                // 此处使用ALL关键字匹配整个PDF内容，而非单个OCR PDF内容的匹配
                String content = OcrPdfRule.REG_EXP_ALL.equals(entry.getKey()) ? wholeContent : (
                        resultMap.get(entry.getKey()) == null ? null : resultMap.get(entry.getKey()).toString()
                );
                // 正则匹配，如果匹配失败则返回false
                if (Strings.isBlank(content) || !Pattern.compile(entry.getValue()).matcher(content).find()) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * 验证模板表达式是否有效
     *
     * @param ocrPdfRule  OCR PDF规则对象
     * @param ocrPdfWhole OCR PDF整体对象
     * @return 如果模板表达式有效，则返回true；否则返回false
     */
    public boolean validateTemplateExpression(OcrPdfRule ocrPdfRule, OcrPdfWhole ocrPdfWhole) {
        if (ocrPdfWhole == null) {
            return false;
        }
        return validateTemplateExpression(ocrPdfRule, ocrPdfWhole.getOcrPdfContents());
    }

    /**
     * 验证模板表达式是否有效
     *
     * @param ocrPdfRule        OCR PDF规则对象
     * @param ocrPdfContentList OCR PDF内容列表
     * @return 如果模板表达式有效，则返回true；否则返回false
     */
    public boolean validateTemplateExpression(OcrPdfRule ocrPdfRule, List<OcrPdfContent> ocrPdfContentList) {
        if (ocrPdfContentList == null || ocrPdfContentList.isEmpty()) {
            return false;
        }
        if (ocrPdfRule == null || ocrPdfRule.getExpression() == null || ocrPdfRule.getExpression().length == 0) {
            return true;
        }
        Map<String, Object> resultMap = OcrPdfContent.toMap(ocrPdfContentList);
        for (String expression : ocrPdfRule.getExpression()) {
            if (Strings.isNotBlank(expression)) {
                Object result = JsProvider.executeExpression(expression, resultMap);
                if (result == null || !Boolean.parseBoolean(result.toString())) {
                    return false;
                }
            }
        }
        return true;
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
     * @param content  字典项名称
     * @param dictCode 字典编码
     * @param type     计算类型，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到匹配的字典项，则返回其字典项编码；否则返回null
     */
    private String calculateItemCode(String content, String dictCode, String type) {
        if (Strings.isBlank(content)) {
            return null;
        }
        // 获取字典项
        List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
        // 比对
        if (dictItemList != null && dictItemList.size() > 0) {
            if ("CONTAINS".equalsIgnoreCase(type)) {
                for (DictItem dictItem : dictItemList) {
                    if (content.indexOf(dictItem.getItemName()) != -1) {
                        return content.replaceAll(dictItem.getItemName(), dictItem.getItemCode());
                    }
                }
            } else {
                for (DictItem dictItem : dictItemList) {
                    if (content.equals(dictItem.getItemName())) {
                        return dictItem.getItemCode();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 根据给定的内容和字典映射字符串，计算并返回对应的值。
     *
     * @param content    需要查找的内容
     * @param dictMapStr 包含字典映射关系的JSON字符串
     * @param type       计算类型，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到对应的内容，则返回对应的值；否则返回null
     * @throws RuntimeException 如果字典映射字符串格式不正确，则抛出运行时异常
     */
    private String calculateRadio(String content, String dictMapStr, String type) {
        if (Strings.isBlank(content)) {
            return null;
        }
        try {
            Map<String, String> dictMap = JSON.parseObject(dictMapStr, Map.class);
            if (dictMap != null && dictMap.size() > 0) {
                if ("CONTAINS".equalsIgnoreCase(type)) {
                    for (Map.Entry<String, String> entry : dictMap.entrySet()) {
                        if (content.indexOf(entry.getKey()) != -1) {
                            return content.replaceAll(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    return dictMap.get(content);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("The data dictionary format is incorrect", e);
        }
        return null;
    }

    /**
     * 根据字典编码和多个字典项名称计算对应的字典项编码
     *
     * @param content  字典项名称列表，以逗号分隔
     * @param dictCode 字典编码
     * @param type     计算类型，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到匹配的字典项，则返回其字典项编码列表，以逗号分隔；否则返回null
     */
    private String calculateItemCodes(String content, String dictCode, String type) {
        List<String> names = OcrUtils.stringToList(content);
        if (names == null || names.size() == 0) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        // 获取字典项
        List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
        // 比对
        if (dictItemList != null && dictItemList.size() > 0) {
            if ("CONTAINS".equalsIgnoreCase(type)) {
                for (String name : names) {
                    for (DictItem dictItem : dictItemList) {
                        if (name.indexOf(dictItem.getItemName()) != -1) {
                            codes.add(name.replaceAll(dictItem.getItemName(), dictItem.getItemCode()));
                        }
                    }
                }
            } else {
                for (DictItem dictItem : dictItemList) {
                    if (names.contains(dictItem.getItemName())) {
                        codes.add(dictItem.getItemCode());
                    }
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
        if (Strings.isBlank(value) || map == null) {
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























