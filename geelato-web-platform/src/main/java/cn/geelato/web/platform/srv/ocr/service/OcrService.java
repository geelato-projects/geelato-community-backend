package cn.geelato.web.platform.srv.ocr.service;

import cn.geelato.core.script.js.JsProvider;
import cn.geelato.plugin.ocr.PDFAnnotationPickContent;
import cn.geelato.plugin.ocr.PDFResolveData;
import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.web.platform.srv.ocr.entity.*;
import cn.geelato.web.platform.srv.ocr.enums.MetaTypeEnum;
import cn.geelato.web.platform.srv.ocr.enums.RuleTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OcrService extends BaseService {

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
        if (ocrPdfContents.isEmpty()) {
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
        // null 替换为空字符串，防止后续处理出错
        content = content == null ? "" : content;
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
                    // null 替换为空字符串，防止后续处理出错
                    content = content == null ? "" : content;
                    // 循环处理中，如果内容为空，且规则的传入值不能为空，则跳过
                    RuleTypeEnum ruleTypeEnum = RuleTypeEnum.lookUp(rule.getType());
                    if (ruleTypeEnum != null) {
                        content = ruleTypeEnum.handle(content, rule, opcMap);
                    } else {
                        throw new RuntimeException("Rule type not supported");
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
     * @param content  待转换的字符串
     * @param dataType 目标数据类型，通过MetaTypeEnum枚举类表示
     * @return 转换后的对象，如果转换失败则返回null
     */
    private Object toFormat(String content, String dataType) {
        MetaTypeEnum metaTypeEnum = MetaTypeEnum.lookUp(dataType);
        if (metaTypeEnum != null) {
            return metaTypeEnum.toFormat(content);
        } else {
            throw new RuntimeException("Data type not supported");
        }
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
        // 正则匹配，非全文匹配，排除ALL关键字
        if (ocrPdfRule == null) {
            return true;
        }
        List<OcrPdfRuleRegExp> ruleRegExps = ocrPdfRule.regExpListExcludeAll();
        if (ruleRegExps == null || ruleRegExps.isEmpty()) {
            return true;
        }
        // 并将PDF内容列表转换为Map对象
        Map<String, Object> resultMap = OcrPdfContent.toMap(ocrPdfContentList);
        for (OcrPdfRuleRegExp regExp : ruleRegExps) {
            String content = resultMap.get(regExp.getLabel()) == null ? null : resultMap.get(regExp.getLabel()).toString();
            boolean isValid = validateTemplateRegExp(content, regExp.getExpression(), regExp.isMatching());
            if (!isValid) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据正则表达式验证模板内容是否匹配。
     *
     * @param content    要验证的内容
     * @param expression 用于验证内容的正则表达式
     * @param isMatching 是否需要匹配到内容
     * @return 如果内容不为空且符合匹配条件则返回true，否则返回false
     */
    public boolean validateTemplateRegExp(String content, String expression, boolean isMatching) {
        if (Strings.isNotBlank(content)) {
            if (isMatching) {// 匹配到
                return Pattern.compile(expression).matcher(content).find();
            } else {// 匹配不到
                return !Pattern.compile(expression).matcher(content).find();
            }
        } else {
            return false;
        }
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
}























