package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.excel.entity.ExportTemplate;
import cn.geelato.web.platform.m.excel.service.ExportTemplateService;
import cn.geelato.web.platform.m.excel.service.ImportExcelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;

/**
 * @author diabl
 * @description: 文件导入
 */
@ApiRestController("/import")
@Slf4j
public class ImportExcelController extends BaseController {

    private final ExportTemplateService exportTemplateService;
    private final ImportExcelService importExcelService;

    @Autowired
    public ImportExcelController(ExportTemplateService exportTemplateService, ImportExcelService importExcelService) {
        this.exportTemplateService = exportTemplateService;
        this.importExcelService = importExcelService;
    }

    /**
     * 下载模板
     *
     * @param request
     * @param response
     * @param templateId
     * @return
     */
    @RequestMapping(value = "/template/{templateId}", method = RequestMethod.GET)
    public ApiResult getTemplate(HttpServletRequest request, HttpServletResponse response, @PathVariable String templateId) {
        try {
            return ApiResult.success(exportTemplateService.getModel(ExportTemplate.class, templateId));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * @param request
     * @param response
     * @param importType part:可以部分导入；all:需要全部导入，错误即中断并回滚。
     * @param templateId 模板文件id
     * @param attachId   业务数据文件id
     * @return
     */
    @RequestMapping(value = "/attach/{importType}/{templateId}/{attachId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult importAttach(HttpServletRequest request, HttpServletResponse response, @PathVariable String importType, @PathVariable String templateId, @PathVariable String attachId) {
        try {
            return importExcelService.importExcel(request, response, importType, templateId, attachId);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiResult.fail(ex.getMessage());
        }
    }

    /**
     * excel导入
     *
     * @param request
     * @param response
     * @param importType part:可以部分导入；all:需要全部导入，错误即中断并回滚。
     * @param templateId 模板文件id
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/file/{importType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult importFile(HttpServletRequest request, HttpServletResponse response, @PathVariable String importType, @PathVariable String templateId) {
        try {
            return importExcelService.importExcel(request, response, importType, templateId, null);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiResult.fail(ex.getMessage());
        }
    }
}
