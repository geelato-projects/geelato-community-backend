package cn.geelato.web.platform.m.ocr.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.ocr.*;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.ocr.entity.OcrPdf;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfContent;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMetaRule;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfWhole;
import cn.geelato.web.platform.m.ocr.enums.LocaleEnum;
import cn.geelato.web.platform.m.ocr.service.OcrPdfService;
import cn.geelato.web.platform.m.ocr.service.OcrService;
import cn.geelato.web.platform.m.ocr.service.OcrUtils;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
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
    PluginBeanProvider pluginBeanProvider;
    @Resource
    private AttachService attachService;

    @Autowired
    public OCRController(PluginBeanProvider pluginBeanProvider, OcrPdfService ocrPdfService, OcrService oService) {
        this.pluginBeanProvider = pluginBeanProvider;
        this.ocrPdfService = ocrPdfService;
        this.oService = oService;
    }

    @RequestMapping(value = "/pdf/meta/{fileId}", method = RequestMethod.GET)
    public ApiResult<List<PDFAnnotationMeta>> meta(@PathVariable String fileId) {
        Attach attach = attachService.getModel(fileId);
        File file = FileUtils.pathToFile(attach.getPath());
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        List<PDFAnnotationMeta> pdfAnnotationMetaList = ocrService.resolvePDFAnnotationMeta(file);
        return ApiResult.success(pdfAnnotationMetaList);
    }

    @RequestMapping(value = "/pdf/resolve0", method = RequestMethod.GET)
    public ApiResult<?> meta(String fileId, String templateId, boolean wholeContent) throws IOException {
        // 需要处理的文件
        Attach file = attachService.getModel(fileId);
        File pdfFile = FileUtils.pathToFile(file.getPath());
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }
        // 获取模板文件
        OcrPdf ocrPdf = ocrPdfService.getModel(templateId, true);
        if (ocrPdf == null || Strings.isBlank(ocrPdf.getTemplate())) {
            throw new IllegalArgumentException("模板不能为空");
        }
        File tempFile = OcrUtils.getTempFile(ocrPdf.getTemplate());
        // 获取OCR服务，解析PDF文件
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        if (wholeContent) {
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(tempFile, pdfFile);
            return ApiResult.success(pdfResolveData);
        } else {
            List<PDFAnnotationPickContent> pdfAnnotationPickContentList = ocrService.pickPDFAnnotationContent(tempFile, pdfFile);
            return ApiResult.success(pdfAnnotationPickContentList);
        }
    }

    /**
     * 解析PDF文件的元数据
     *
     * @param fileId       PDF文件的附件id
     * @param templateId   PDF模板id
     * @param wholeContent 是否返回整个PDF文件的内容
     * @return ApiResult<?> 如果解析成功，则返回包含成功信息的ApiResult对象；如果解析失败，则返回包含错误信息的ApiResult对象
     * @throws Exception 当发生异常时抛出
     */
    @RequestMapping(value = "/pdf/resolve", method = RequestMethod.GET)
    public ApiResult<?> metaResolve(String fileId, String templateId, boolean wholeContent) throws IOException, ParseException {
        // 需要处理的文件
        Attach file = attachService.getModel(fileId);
        File pdfFile = FileUtils.pathToFile(file.getPath());
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }
        // 获取模板文件
        OcrPdf ocrPdf = ocrPdfService.getModel(templateId, true);
        if (ocrPdf == null || Strings.isBlank(ocrPdf.getTemplate())) {
            throw new IllegalArgumentException("模板不能为空");
        }
        File tempFile = OcrUtils.getTempFile(ocrPdf.getTemplate());
        // 获取OCR服务，解析PDF文件
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        if (wholeContent) {
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(tempFile, pdfFile);
            OcrPdfWhole ocrPdfWhole = oService.formatContent(pdfResolveData, ocrPdf.getMetas());
            return ApiResult.success(ocrPdfWhole);
        } else {
            List<PDFAnnotationPickContent> pdfAnnotationPickContentList = ocrService.pickPDFAnnotationContent(tempFile, pdfFile);
            List<OcrPdfContent> ocrPdfContentList = oService.formatContent(pdfAnnotationPickContentList, ocrPdf.getMetas());
            return ApiResult.success(ocrPdfContentList);
        }
    }

    /**
     * 测试PDF元数据规则接口
     *
     * @param data 包含请求数据的Map对象，其中包括"content"（内容字符串）、"rule"（规则字符串）和"metas"（元数据字符串）三个字段
     * @return ApiResult对象，包含处理结果
     * @throws ParseException 如果解析请求数据时出现异常，则抛出此异常
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
        File tempFile = OcrUtils.getTempFile(form.getTemplate());
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
        if (format.indexOf(OcrUtils.TIME_ZONE_SIGN) != -1 && Strings.isBlank(timeZone)) {
            throw new IllegalArgumentException("time zone cannot be empty");
        }
        // 获取默认语言环境
        Locale localeObj = LocaleEnum.getDefaultLocale(locale);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(format, localeObj);
        // 获取当前时间
        ZonedDateTime nowTime = ZonedDateTime.now();
        if (format.indexOf(OcrUtils.TIME_ZONE_SIGN) != -1 && Strings.isNotBlank(timeZone)) {
            nowTime = ZonedDateTime.now(ZoneId.of(timeZone));
        }
        return ApiResult.success(dtf.format(nowTime));
    }
}
