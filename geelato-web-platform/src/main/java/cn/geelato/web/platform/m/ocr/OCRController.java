package cn.geelato.web.platform.m.ocr;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.ocr.OCRService;
import cn.geelato.plugin.ocr.PDFAnnotationMeta;
import cn.geelato.plugin.ocr.PluginInfo;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.service.AttachService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.util.List;

@ApiRestController(value = "/ocr")
@Slf4j
public class OCRController extends BaseController {
    @Resource
    private AttachService attachService;
    PluginBeanProvider pluginBeanProvider;

    @Autowired
    public OCRController(PluginBeanProvider pluginBeanProvider){
        this.pluginBeanProvider=pluginBeanProvider;
    }
    @RequestMapping(value = "/pdf/meta/{fileId}", method = RequestMethod.GET)
    public ApiResult<List<PDFAnnotationMeta>> meta(@PathVariable String fileId){
        Attach attach = attachService.getModel(fileId);
        File file = FileUtils.pathToFile(attach.getPath());
        OCRService ocrService= pluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId);
        List<PDFAnnotationMeta> pdfAnnotationMetaList=ocrService.resolvePDFAnnotationMeta(file);
        return ApiResult.success(pdfAnnotationMetaList);
    }
}
