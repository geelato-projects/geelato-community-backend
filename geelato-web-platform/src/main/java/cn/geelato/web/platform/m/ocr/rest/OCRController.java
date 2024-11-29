package cn.geelato.web.platform.m.ocr.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.ocr.*;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.entity.Base64Info;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.ocr.entity.OcrPdf;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfContent;
import cn.geelato.web.platform.m.ocr.enums.LocaleEnum;
import cn.geelato.web.platform.m.ocr.service.OcrPdfService;
import cn.geelato.web.platform.m.ocr.service.OcrService;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApiRestController(value = "/ocr")
@Slf4j
public class OCRController extends BaseController {
    PluginBeanProvider pluginBeanProvider;
    @Resource
    private AttachService attachService;
    private final OcrPdfService ocrPdfService;
    private final OcrService oService;

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

    @RequestMapping(value = "/pdf/resolve", method = RequestMethod.GET)
    public ApiResult<?> meta(String fileId, String templateId, boolean wholeContent) {
        Attach file = attachService.getModel(fileId);
        Attach template = attachService.getModel(templateId);
        File pdfFile = FileUtils.pathToFile(file.getPath());
        File templateFile = FileUtils.pathToFile(template.getPath());
        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        if (wholeContent) {
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(templateFile, pdfFile);
            return ApiResult.success(pdfResolveData);
        } else {
            List<PDFAnnotationPickContent> pdfAnnotationPickContentList = ocrService.pickPDFAnnotationContent(templateFile, pdfFile);
            return ApiResult.success(pdfAnnotationPickContentList);
        }
    }

    /**
     * 解析PDF文件的元数据
     *
     * @param fileId     PDF文件的附件id
     * @param templateId PDF模板id
     * @return ApiResult<?> 如果解析成功，则返回包含成功信息的ApiResult对象；如果解析失败，则返回包含错误信息的ApiResult对象
     * @throws Exception 当发生异常时抛出
     */
    @RequestMapping(value = "/pdf/meta/resolve", method = RequestMethod.GET)
    public ApiResult<?> metaResolve(String fileId, String templateId) {
        try {
            Attach file = attachService.getModel(fileId);
            OcrPdf ocrPdf = ocrPdfService.getModel(templateId, true);
            if (Strings.isBlank(ocrPdf.getTemplate())) {
                throw new IllegalArgumentException("模板不能为空");
            }
            File pdfFile = FileUtils.pathToFile(file.getPath());
            File tempFile = this.getTempFile(ocrPdf.getTemplate());
            OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
            List<PDFAnnotationPickContent> pdfAnnotationPickContentList = ocrService.pickPDFAnnotationContent(tempFile, pdfFile);
            if (pdfAnnotationPickContentList == null || pdfAnnotationPickContentList.size() == 0) {
                return ApiResult.success(pdfAnnotationPickContentList);
            }
            List<OcrPdfContent> ocrPdfContentList = oService.formatContent(pdfAnnotationPickContentList, ocrPdf.getMetas());
            return ApiResult.success(pdfAnnotationPickContentList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    /**
     * 分析PDF文件
     *
     * @param form 包含PDF文件和模板信息的表单对象
     * @return ApiResult 包含分析结果的响应对象
     * @throws IllegalArgumentException 如果模板为空或格式错误，则抛出此异常
     */
    @RequestMapping(value = "/pdf/analysis", method = RequestMethod.POST)
    public ApiResult analysis(@RequestBody OcrPdf form) {
        try {
            if (Strings.isBlank(form.getTemplate())) {
                throw new IllegalArgumentException("模板不能为空");
            }
            File tempFile = getTempFile(form.getTemplate());
            OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
            List<PDFAnnotationMeta> pdfAnnotationMetaList = ocrService.resolvePDFAnnotationMeta(tempFile);
            return ApiResult.success(pdfAnnotationMetaList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
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
        try {
            if (Strings.isBlank(format)) {
                throw new IllegalArgumentException("时间格式不能为空");
            }
            if (format.indexOf("zzz") != -1 && Strings.isBlank(timeZone)) {
                throw new IllegalArgumentException("时区不能为空");
            }
            Locale localeObj = LocaleEnum.getDefaultLocale(locale);
            DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern(format, localeObj);
            ZonedDateTime nowTime = ZonedDateTime.now();
            if (format.indexOf("zzz") != -1 && Strings.isNotBlank(timeZone)) {
                nowTime = ZonedDateTime.now(ZoneId.of(timeZone));
            }
            return ApiResult.success(utcFormatter.format(nowTime));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 根据Base64编码的字符串生成临时文件
     *
     * @param base64 包含Base64编码信息的字符串
     * @return 生成的临时文件对象
     * @throws IOException      如果在文件操作过程中发生I/O错误，则抛出此异常
     * @throws RuntimeException 如果提供的Base64信息格式错误，则抛出此异常，并附带“模板格式错误”的错误信息
     */
    private File getTempFile(String base64) throws IOException {
        File tempFile = null;
        Base64Info bi = JSON.parseObject(base64, Base64Info.class);
        if (bi != null && Strings.isNotBlank(bi.getName()) && Strings.isNotBlank(bi.getBase64())) {
            byte[] decodedBytes = Base64.getDecoder().decode(bi.getBase64());
            String fileExt = bi.getName().substring(bi.getName().lastIndexOf("."));
            tempFile = File.createTempFile("temp_base64_ocrpdf_analysis" + UUID.randomUUID(), fileExt);
            tempFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(decodedBytes);
            }
        } else {
            throw new RuntimeException("模板格式错误");
        }
        return tempFile;
    }
}
