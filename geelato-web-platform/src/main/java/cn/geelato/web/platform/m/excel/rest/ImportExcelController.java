package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.web.platform.m.excel.entity.ExportTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.excel.service.ExportTemplateService;
import cn.geelato.web.platform.m.excel.service.ImportExcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

/**
 * @author diabl
 * @description: 文件导入
 */
@Controller
@RequestMapping(value = "/api/import")
public class ImportExcelController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(ImportExcelController.class);

    @Autowired
    private ExportTemplateService exportTemplateService;
    @Autowired
    private ImportExcelService importExcelService;

    /**
     * 下载模板
     *
     * @param request
     * @param response
     * @param templateId
     * @return
     */
    @RequestMapping(value = "/template/{templateId}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult getTemplate(HttpServletRequest request, HttpServletResponse response, @PathVariable String templateId) {
        ApiResult result = new ApiResult();
        try {
            result.setData(exportTemplateService.getModel(ExportTemplate.class, templateId));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
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
    @ResponseBody
    public ApiResult importAttach(HttpServletRequest request, HttpServletResponse response, @PathVariable String importType, @PathVariable String templateId, @PathVariable String attachId) {
        ApiResult result = new ApiResult();
        try {
            result = importExcelService.importExcel(request, response, importType, templateId, attachId);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error(ex);
        }

        return result;
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
    @ResponseBody
    public ApiResult importFile(HttpServletRequest request, HttpServletResponse response, @PathVariable String importType, @PathVariable String templateId) {
        ApiResult result = new ApiResult();
        try {
            result = importExcelService.importExcel(request, response, importType, templateId, null);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error(ex);
        }

        return result;
    }
}
