package cn.geelato.web.platform.resolve.artifact;

import cn.geelato.web.platform.plugin.PluginBeanProvider;
import cn.geelato.plugin.ocr.OCRService;
import cn.geelato.plugin.ocr.PDFAnnotationMeta;
import cn.geelato.plugin.ocr.PDFResolveData;
import cn.geelato.plugin.ocr.PluginInfo;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.resolve.core.ResolveArtifact;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.srv.ocr.entity.*;
import cn.geelato.web.platform.srv.ocr.service.OcrPdfService;
import cn.geelato.web.platform.srv.ocr.service.OcrService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class TemplateResolveArtifact implements ResolveArtifact {
    private final PluginBeanProvider pluginBeanProvider;
    private final OcrPdfService ocrPdfService;
    private final OcrService ocrRuleService;

    public TemplateResolveArtifact(PluginBeanProvider pluginBeanProvider, OcrPdfService ocrPdfService, OcrService ocrRuleService) {
        this.pluginBeanProvider = pluginBeanProvider;
        this.ocrPdfService = ocrPdfService;
        this.ocrRuleService = ocrRuleService;
    }

    @Override
    public String getId() {
        return "file.template.resolve";
    }

    @Override
    public boolean supports(ResolveContext ctx) {
        if (ctx == null || ctx.getParams() == null) {
            return false;
        }
        return Strings.isNotBlank(ctx.getParams().getString("templateId"));
    }

    @Override
    public Object execute(ResolveContext ctx) throws Exception {
        File pdfFile = ctx.getSourceFile();
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("file not found");
        }

        String templateId = ctx.getParams().getString("templateId");
        boolean wholeContent = ctx.getParams().getBooleanValue("wholeContent");

        List<String> pdfIds = StringUtils.toListDr(templateId);
        if (pdfIds.isEmpty()) {
            throw new IllegalArgumentException("templateId is blank");
        }

        OCRService ocrService = pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        for (String pdfId : pdfIds) {
            OcrPdf ocrPdf = ocrPdfService.getModel(pdfId, true);
            if (ocrPdf == null || Strings.isBlank(ocrPdf.getTemplate()) || ocrPdf.getMetas() == null || ocrPdf.getMetas().isEmpty()) {
                continue;
            }

            OcrPdfRule ocrPdfRule = ocrPdf.toRules();
            if (!validateTemplateRegExpAll(ocrService, pdfFile, ocrPdfRule)) {
                continue;
            }

            List<PDFAnnotationMeta> pdfAnnotationMetaList = OcrPdfMeta.toPDFAnnotationMetaList(ocrPdf.getMetas());
            PDFResolveData pdfResolveData = ocrService.resolvePDFFile(pdfAnnotationMetaList, pdfFile);
            OcrPdfWhole ocrPdfWhole = ocrRuleService.formatContent(pdfResolveData, ocrPdf.getMetas());

            if (!ocrRuleService.validateTemplateRegExp(ocrPdfRule, ocrPdfWhole)) {
                continue;
            }

            Object result = wholeContent ? ocrPdfWhole : ocrPdfWhole.getOcrPdfContents();
            ctx.putArtifactData("pdf.templateId", pdfId);
            ctx.setResult(result);
            return result;
        }

        throw new IllegalArgumentException("Template matching failure");
    }

    private boolean validateTemplateRegExpAll(OCRService ocrService, File pdfFile, OcrPdfRule ocrPdfRule) {
        if (ocrPdfRule != null && ocrPdfRule.getRegexp() != null && !ocrPdfRule.getRegexp().isEmpty()) {
            List<OcrPdfRuleRegExp> ruleRegExps = ocrPdfRule.regExpIncludeAll();
            if (ruleRegExps == null || ruleRegExps.isEmpty()) {
                return true;
            }
            String wholeContent = ocrService.pickPDFWholeContent(pdfFile);
            for (OcrPdfRuleRegExp regExp : ruleRegExps) {
                boolean isValid = ocrRuleService.validateTemplateRegExp(wholeContent, regExp.getExpression(), regExp.isMatching());
                if (!isValid) {
                    return false;
                }
            }
        }
        return true;
    }
}
