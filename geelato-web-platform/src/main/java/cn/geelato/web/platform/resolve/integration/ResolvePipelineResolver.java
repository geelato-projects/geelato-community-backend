package cn.geelato.web.platform.resolve.integration;

import cn.geelato.web.platform.resolve.core.ResolveContext;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
public class ResolvePipelineResolver {
    public static final String FEATURE_PDF_TEMPLATE = "pdf.template";
    public static final String FEATURE_PDF_MINERU_NATURAL = "pdf.mineru.natural";
    public static final String FEATURE_PDF_MINERU_AI = "pdf.mineru.ai";

    public static final String BIZTAG_CARRIER_SO_PARSE = "carrier.so.parse";
    public static final String BIZTAG_CARRIER_SI_PARSE = "carrier.si.parse";
    public static final String BIZTAG_QUOTE_SHEET_PARSE = "quote.sheet.parse";
    public static final String BIZTAG_BOOKING_CONFIRM_PARSE = "booking.confirm.parse";
    public static final String BIZTAG_INVOICE_PARSE = "invoice.parse";

    public static final String CHANNEL_PDF_TEMPLATE = "resolvePdfTemplateInput";
    public static final String CHANNEL_PDF_MINERU_NATURAL = "resolvePdfMineruNaturalInput";
    public static final String CHANNEL_PDF_MINERU_AI = "resolvePdfMineruAiInput";

    public String resolveChannelName(ResolveContext ctx) {
        String biztag = ctx == null ? null : ctx.getBiztag();
        String feature = ctx == null ? null : ctx.getFeature();
        String pipelineKey = Strings.isNotBlank(biztag) ? biztag : feature;
        if (Strings.isNotBlank(pipelineKey)) {
            if (BIZTAG_CARRIER_SO_PARSE.equals(pipelineKey) || BIZTAG_CARRIER_SI_PARSE.equals(pipelineKey)) {
                return CHANNEL_PDF_MINERU_AI;
            }
            if (BIZTAG_QUOTE_SHEET_PARSE.equals(pipelineKey) || BIZTAG_BOOKING_CONFIRM_PARSE.equals(pipelineKey) || BIZTAG_INVOICE_PARSE.equals(pipelineKey)) {
                return CHANNEL_PDF_MINERU_NATURAL;
            }
            if (FEATURE_PDF_TEMPLATE.equals(pipelineKey)) {
                if (ctx.getParams() == null || Strings.isBlank(ctx.getParams().getString("templateId"))) {
                    throw new IllegalArgumentException("templateId is required for feature: " + FEATURE_PDF_TEMPLATE);
                }
                return CHANNEL_PDF_TEMPLATE;
            }
            if (FEATURE_PDF_MINERU_AI.equals(pipelineKey)) {
                return CHANNEL_PDF_MINERU_AI;
            }
            if (FEATURE_PDF_MINERU_NATURAL.equals(pipelineKey)) {
                return CHANNEL_PDF_MINERU_NATURAL;
            }
        }

        String ext = ctx == null ? null : ctx.getSourceExt();
        if (Strings.isBlank(ext)) {
            throw new IllegalArgumentException("file type is blank");
        }
        String upper = ext.toUpperCase();
        if (".PDF".equals(upper) || ".DOC".equals(upper) || ".DOCX".equals(upper) || ".XLS".equals(upper) || ".XLSX".equals(upper)) {
            return CHANNEL_PDF_MINERU_NATURAL;
        }
        throw new IllegalArgumentException("file type not supported: " + ext);
    }
}
