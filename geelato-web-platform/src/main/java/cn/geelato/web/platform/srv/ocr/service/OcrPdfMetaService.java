package cn.geelato.web.platform.srv.ocr.service;

import cn.geelato.web.platform.srv.base.service.BaseSortableService;
import cn.geelato.web.platform.srv.ocr.entity.OcrPdfMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OcrPdfMetaService extends BaseSortableService {

    /**
     * 根据PDF ID查询OCR PDF元数据模型列表
     *
     * @param pdfId PDF文件的唯一标识符
     * @return OCR PDF元数据模型列表，如果未查询到任何数据，则返回空列表
     */
    public List<OcrPdfMeta> queryModelByPdfId(String pdfId) {
        Map<String, Object> params = new HashMap<>();
        params.put("pdfId", pdfId);
        return this.queryModel(OcrPdfMeta.class, params);
    }

    /**
     * 根据PDF ID删除相关的OCR PDF元数据模型
     *
     * @param pdfId PDF文件的唯一标识符
     */
    public void isDeleteModelByPdfId(String pdfId) {
        List<OcrPdfMeta> ocrPdfMetas = this.queryModelByPdfId(pdfId);
        if (ocrPdfMetas != null && !ocrPdfMetas.isEmpty()) {
            for (OcrPdfMeta ocrPdfMeta : ocrPdfMetas) {
                this.isDeleteModel(ocrPdfMeta);
            }
        }
    }

    public void updateMetaRules(String pdfId, Map<String, OcrPdfMeta> metaRules) {
        if (metaRules == null || metaRules.isEmpty()) {
            return;
        }
        List<OcrPdfMeta> ocrPdfMetas = this.queryModelByPdfId(pdfId);
        if (ocrPdfMetas != null && !ocrPdfMetas.isEmpty()) {
            for (OcrPdfMeta ocrPdfMeta : ocrPdfMetas) {
                OcrPdfMeta meta = metaRules.get(ocrPdfMeta.getName());
                if (meta != null) {
                    ocrPdfMeta.setRule(Strings.isNotBlank(meta.getRule()) ? meta.getRule() : null);
                    ocrPdfMeta.setDiscernRule(Strings.isNotBlank(meta.getDiscernRule()) ? meta.getDiscernRule() : null);
                    ocrPdfMeta.setFloatAreaY(meta.getFloatAreaY());
                    ocrPdfMeta.setLineHeight(meta.getLineHeight());
                    this.updateModel(ocrPdfMeta);
                }
            }
        }
    }
}
