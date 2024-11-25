package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.security.entity.OcrPdfMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OcrPdfMetaService extends BaseService {

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
}
