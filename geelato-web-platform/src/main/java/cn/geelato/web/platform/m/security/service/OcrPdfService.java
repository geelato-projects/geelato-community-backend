package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.security.entity.OcrPdf;
import cn.geelato.web.platform.m.security.entity.OcrPdfMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class OcrPdfService extends BaseService {
    @Lazy
    @Autowired
    private OcrPdfMetaService ocrPdfMetaService;

    /**
     * 判断并删除OCR PDF模型及其子表数据
     *
     * @param model OCR PDF模型对象
     */
    public void isDeleteModel(OcrPdf model) {
        // 删除自己
        super.isDeleteModel(model);
        // 删除子表
        ocrPdfMetaService.isDeleteModelByPdfId(model.getId());
    }

    /**
     * 获取OCR PDF模型
     *
     * @param id      OCR PDF模型的ID
     * @param hasMeta 是否需要包含子表数据，true表示包含，false表示不包含
     * @return OCR PDF模型对象
     */
    public OcrPdf getModel(String id, Boolean hasMeta) {
        // 获取自己
        OcrPdf model = super.getModel(OcrPdf.class, id);
        // 获取子表
        List<OcrPdfMeta> ocrPdfMetas = new ArrayList<>();
        if (hasMeta != null && hasMeta.booleanValue()) {
            ocrPdfMetas = ocrPdfMetaService.queryModelByPdfId(id);
        }
        model.setMetas(ocrPdfMetas);

        return model;
    }


    /**
     * 创建OCR PDF模型及其子表数据
     *
     * @param model OCR PDF模型对象
     * @return 创建后的OCR PDF模型对象
     */
    public OcrPdf createModel(OcrPdf model) {
        // 创建自己
        OcrPdf op = super.createModel(model);
        // 创建子表
        op.setMetas(model.getMetas());
        createOcrPdfMeta(op);

        return op;
    }


    /**
     * 更新OCR PDF模型及其子表数据
     *
     * @param model OCR PDF模型对象
     * @return 更新后的OCR PDF模型对象
     */
    public OcrPdf updateModel(OcrPdf model) {
        // 更新自己
        OcrPdf op = super.updateModel(model);
        // 删除子表
        ocrPdfMetaService.isDeleteModelByPdfId(model.getId());
        // 更新子表
        op.setMetas(model.getMetas());
        createOcrPdfMeta(op);

        return op;
    }

    /**
     * 创建OCR PDF模型的子表数据
     *
     * @param model OCR PDF模型对象
     */
    private void createOcrPdfMeta(OcrPdf model) {
        List<OcrPdfMeta> ocrPdfMetas = new ArrayList<>();
        if (model.getMetas() != null && !model.getMetas().isEmpty()) {
            for (OcrPdfMeta opm : model.getMetas()) {
                opm.setPdfId(model.getId());
                opm.setAppId(model.getAppId());
                opm.setTenantCode(model.getTenantCode());
                ocrPdfMetas.add(ocrPdfMetaService.createModel(opm));
            }
        }
        model.setMetas(ocrPdfMetas);
    }
}
