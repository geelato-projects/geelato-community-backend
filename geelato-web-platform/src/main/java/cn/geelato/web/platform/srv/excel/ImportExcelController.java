package cn.geelato.web.platform.srv.excel;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.ExportTemplate;
import cn.geelato.web.platform.srv.excel.service.ExportTemplateService;
import cn.geelato.web.platform.srv.excel.service.ImportExcelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author diabl
 * 文件导入
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
     * <p>
     * 根据提供的模板ID下载对应的模板文件。
     *
     * @param templateId 模板ID，用于指定要下载的模板
     * @return 返回ApiResult对象，包含操作结果和模板数据
     */
    @RequestMapping(value = "/template/{templateId}", method = RequestMethod.GET)
    public ApiResult<?> getTemplate(@PathVariable String templateId) {
        try {
            return ApiResult.success(exportTemplateService.getModel(ExportTemplate.class, templateId));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 导入附件
     * <p>
     * 根据指定的导入类型、模板文件ID和业务数据文件ID，执行附件的导入操作。
     *
     * @param importType 导入类型，可选值为 "part"（部分导入）或 "all"（全部导入，错误时中断并回滚）
     * @param templateId 模板文件ID，用于指定导入时使用的模板
     * @param attachId   业务数据文件ID，标识要导入的业务数据文件
     * @return 返回操作结果，包括成功或失败的信息
     */
    @RequestMapping(value = "/attach/{importType}/{templateId}/{attachId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> importAttach(@PathVariable String importType, @PathVariable String templateId, @PathVariable String attachId, String index) {
        try {
            // 调用importExcel方法执行导入操作
            return importExcelService.importExcel(this.request, this.response, importType, templateId, index, attachId);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiResult.fail(ex.getMessage());
        }
    }

    /**
     * Excel文件导入
     * <p>
     * 根据提供的导入类型和模板文件ID，将Excel文件导入系统。
     *
     * @param importType 导入类型，可选值为"part"（部分导入）和"all"（全部导入，遇到错误即中断并回滚）
     * @param templateId 模板文件ID，用于指定导入数据的模板
     * @return 返回一个包含导入结果的ApiResult对象
     */
    @RequestMapping(value = "/file/{importType}/{templateId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> importFile(@PathVariable String importType, @PathVariable String templateId, String index) {
        try {
            // 调用importExcel方法执行导入操作
            return importExcelService.importExcel(this.request, this.response, importType, templateId, index, null);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return ApiResult.fail(ex.getMessage());
        }
    }
}
