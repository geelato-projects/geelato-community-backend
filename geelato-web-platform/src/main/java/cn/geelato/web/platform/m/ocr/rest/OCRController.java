package cn.geelato.web.platform.m.ocr.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.ocr.*;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.enums.LocaleEnum;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.ocr.entity.*;
import cn.geelato.web.platform.m.ocr.service.OcrPdfService;
import cn.geelato.web.platform.m.ocr.service.OcrService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApiRestController(value = "/ocr")
@Slf4j
public class OCRController extends BaseController {
    private final OcrPdfService ocrPdfService;
    private final OcrService oService;
    private final FileHandler fileHandler;
    PluginBeanProvider pluginBeanProvider;

    @Autowired
    public OCRController(PluginBeanProvider pluginBeanProvider, OcrPdfService ocrPdfService, OcrService oService, FileHandler fileHandler) {
        this.pluginBeanProvider = pluginBeanProvider;
        this.ocrPdfService = ocrPdfService;
        this.oService = oService;
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/pdf/meta/{fileId}", method = RequestMethod.GET)
    public ApiResult<List<PDFAnnotationMeta>> meta(@PathVariable String fileId) {
        File file = fileHandler.toFile(fileId);
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        List<PDFAnnotationMeta> pdfAnnotationMetaList = ocrService.resolvePDFAnnotationMeta(file);
        return ApiResult.success(pdfAnnotationMetaList);
    }

    @RequestMapping(value = "/pdf/resolve0", method = RequestMethod.GET)
    public ApiResult<?> meta(String fileId, String templateId, boolean wholeContent) throws IOException {
        // 需要处理的文件
        File pdfFile = fileHandler.toFile(fileId);
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }
        // 获取模板文件
        OcrPdf ocrPdf = ocrPdfService.getModel(templateId, true);
        if (ocrPdf == null || Strings.isBlank(ocrPdf.getTemplate())) {
            throw new IllegalArgumentException("模板不能为空");
        }
        if (ocrPdf.getMetas() == null || ocrPdf.getMetas().isEmpty()) {
            throw new IllegalArgumentException("模板元数据不能为空");
        }
        List<PDFAnnotationMeta> pdfAnnotationMetaList = OcrPdfMeta.toPDFAnnotationMetaList(ocrPdf.getMetas());
        // 获取OCR服务，解析PDF文件
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        if (wholeContent) {
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(pdfAnnotationMetaList, pdfFile);
            return ApiResult.success(pdfResolveData);
        } else {
            List<PDFAnnotationPickContent> pdfAnnotationPickContentList = ocrService.pickPDFAnnotationContent(pdfAnnotationMetaList, pdfFile);
            return ApiResult.success(pdfAnnotationPickContentList);
        }
    }

    @RequestMapping(value = "/pdf/content/clear", method = RequestMethod.POST)
    public ApiResult<?> meta(String fileId, @RequestBody List<AnnotationPositionMeta> annotationPositionMetaList) throws IOException {
        File fileInstance = fileHandler.toFile(fileId);
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        File targetFile = File.createTempFile("ocr_clear", ".pdf", new File("C:\\Users\\39139\\Desktop\\testfile"));
        ocrService.clearContent(annotationPositionMetaList, fileInstance, targetFile);
        return ApiResult.success(targetFile);
    }

    /**
     * 解析PDF文件的元数据
     *
     * @param fileId       PDF文件的附件id
     * @param templateId   PDF模板id
     * @param wholeContent 是否返回整个PDF文件的内容
     * @return ApiResult<?> 如果解析成功，则返回包含成功信息的ApiResult对象；如果解析失败，则返回包含错误信息的ApiResult对象
     */
    @RequestMapping(value = "/pdf/resolve", method = RequestMethod.GET)
    public ApiResult<?> metaResolve(String fileId, String templateId, boolean wholeContent) throws IOException, ParseException {
        // 需要处理的文件
        File pdfFile = fileHandler.toFile(fileId);
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }
        List<String> pdfIds = StringUtils.toListDr(templateId);
        if (pdfIds.isEmpty()) {
            throw new IllegalArgumentException("templateId is blank");
        }
        // 获取OCR服务，解析PDF文件
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        // 遍历模板id列表，逐个处理每个PDF文件
        for (String pdfId : pdfIds) {
            // 获取模板信息
            OcrPdf ocrPdf = ocrPdfService.getModel(pdfId, true);
            if (ocrPdf == null || Strings.isBlank(ocrPdf.getTemplate()) || ocrPdf.getMetas() == null || ocrPdf.getMetas().isEmpty()) {
                continue;
            }
            // 获取模板检验规则
            OcrPdfRule ocrPdfRule = ocrPdf.toRules();
            // 是否存在全文正则匹配，如果存在，则进行匹配
            if (!this.validateTemplateRegExpAll(ocrService, pdfFile, ocrPdfRule)) {
                continue;
            }
            // 模板元数据转换为PDF注释元数据列表
            List<PDFAnnotationMeta> pdfAnnotationMetaList = OcrPdfMeta.toPDFAnnotationMetaList(ocrPdf.getMetas());
            // 获取文件信息，标记，全文
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(pdfAnnotationMetaList, pdfFile);
            // 格式化内容
            OcrPdfWhole ocrPdfWhole = oService.formatContent(pdfResolveData, ocrPdf.getMetas());
            // 验证提取内容是否与正则匹配
            if (!oService.validateTemplateRegExp(ocrPdfRule, ocrPdfWhole)) {
                continue;
            }
            if (wholeContent) {
                return ApiResult.success(ocrPdfWhole);
            } else {
                return ApiResult.success(ocrPdfWhole.getOcrPdfContents());
            }
        }
        return ApiResult.fail("Template matching failure");
    }

    /**
     * 测试PDF元数据规则接口
     *
     * @param data 包含请求数据的Map对象，其中包括"content"（内容字符串）、"rule"（规则字符串）和"metas"（元数据字符串）三个字段
     * @return ApiResult对象，包含处理结果
     */
    @RequestMapping(value = "/pdf/meta/test", method = RequestMethod.POST)
    public ApiResult<?> ruleTest(@RequestBody Map<String, Object> data) {
        String content = data.get("content") == null ? null : data.get("content").toString();
        String ruleStr = data.get("rule") == null ? null : data.get("rule").toString();
        String metaStr = data.get("metas") == null ? null : data.get("metas").toString();
        List<OcrPdfMetaRule> rules = JSON.parseArray(ruleStr, OcrPdfMetaRule.class);
        List<OcrPdfContent> metas = JSON.parseArray(metaStr, OcrPdfContent.class);
        String result = oService.ruleTest(content, rules, metas);
        return ApiResult.success(result);
    }

    /**
     * 分析PDF文件
     *
     * @param form 包含PDF文件和模板信息的表单对象
     * @return ApiResult 包含分析结果的响应对象
     * @throws IllegalArgumentException 如果模板为空或格式错误，则抛出此异常
     */
    @RequestMapping(value = "/pdf/analysis", method = RequestMethod.POST)
    public ApiResult analysis(@RequestBody OcrPdf form) throws IOException {
        if (Strings.isBlank(form.getTemplate())) {
            throw new IllegalArgumentException("pdf template is blank");
        }
        File tempFile = Base64Helper.toTempFile(form.getTemplate());
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        List<PDFAnnotationMeta> pdfAnnotationMetaList = ocrService.resolvePDFAnnotationMeta(tempFile);
        return ApiResult.success(pdfAnnotationMetaList);
    }

    /**
     * 验证时间格式
     *
     * @param format   时间格式字符串
     * @param timeZone 时区字符串
     * @param locale   地区字符串
     * @return ApiResult<?> 包含操作结果的响应对象
     */
    @RequestMapping(value = "/time/validate", method = RequestMethod.GET)
    public ApiResult<?> validateTime(String format, String timeZone, String locale) {
        if (Strings.isBlank(format)) {
            throw new IllegalArgumentException("time format cannot be empty");
        }
        if (format.contains(DateUtils.TIME_ZONE_SIGN) && Strings.isBlank(timeZone)) {
            throw new IllegalArgumentException("time zone cannot be empty");
        }
        // 获取默认语言环境
        Locale localeObj = LocaleEnum.getDefaultLocale(locale);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(format, localeObj);
        // 获取当前时间
        ZonedDateTime nowTime = ZonedDateTime.now();
        if (format.contains(DateUtils.TIME_ZONE_SIGN) && Strings.isNotBlank(timeZone)) {
            nowTime = ZonedDateTime.now(ZoneId.of(timeZone));
        }
        return ApiResult.success(dtf.format(nowTime));
    }

    /**
     * 验证PDF文件是否满足正则表达式规则
     *
     * @param ocrService OCR服务接口
     * @param pdfFile    PDF文件
     * @param ocrPdfRule OCR PDF规则
     * @return 如果PDF文件满足所有正则表达式规则，则返回true；否则返回false
     */
    private boolean validateTemplateRegExpAll(OCRService ocrService, File pdfFile, OcrPdfRule ocrPdfRule) {
        if (ocrPdfRule != null && ocrPdfRule.getRegexp() != null && !ocrPdfRule.getRegexp().isEmpty()) {
            // 正则匹配，全文匹配
            OcrPdfRuleRegExp regExp = ocrPdfRule.regExpIncludeAll();
            if (regExp != null) {
                // 查询全文内容
                String wholeContent = ocrService.pickPDFWholeContent(pdfFile);
                return oService.validateTemplateRegExp(wholeContent, regExp.getExpression(), regExp.isMatching());
            }
        }
        return true;
    }
}
