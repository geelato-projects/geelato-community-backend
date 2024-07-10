package cn.geelato.web.platform.m.excel.rest;

import cn.geelato.web.platform.m.excel.entity.ExportTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiPagedResult;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.excel.service.ExportTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @date 2023/8/11 15:05
 */
@Controller
@RequestMapping(value = "/api/export/template")
public class ExportTemplateController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<ExportTemplate> CLAZZ = ExportTemplate.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "fileCode"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(ExportTemplateController.class);
    @Autowired
    private ExportTemplateService exportTemplateService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            // FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            // result = exportTemplateService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result = exportTemplateService.pageQueryModel("page_query_platform_export_template", params, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(exportTemplateService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            result.setData(exportTemplateService.getModel(CLAZZ, id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody ExportTemplate form) {
        ApiResult result = new ApiResult();
        try {
            ExportTemplate model = new ExportTemplate();
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                model = exportTemplateService.updateModel(form);
            } else {
                model = exportTemplateService.createModel(form);
            }
            exportTemplateService.generateFile(model.getId(), "template");
            result.setData(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult isDelete(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            ExportTemplate model = exportTemplateService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            exportTemplateService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/generateFile/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult generateFile(@PathVariable(required = true) String id, @RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            String fileType = (String) params.get("fileType");
            result = exportTemplateService.generateFile(id, fileType);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }
}
