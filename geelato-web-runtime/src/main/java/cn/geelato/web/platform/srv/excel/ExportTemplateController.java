package cn.geelato.web.platform.srv.excel;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.ExportTemplate;
import cn.geelato.web.platform.srv.excel.service.ExportTemplateService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/export/template")
@Slf4j
public class ExportTemplateController extends BaseController {
    private static final Class<ExportTemplate> CLAZZ = ExportTemplate.class;
    private final ExportTemplateService exportTemplateService;

    @Autowired
    public ExportTemplateController(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult<?> pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return exportTemplateService.pageQueryModel("page_query_platform_export_template", params, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult<?> query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(exportTemplateService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable() String id) {
        try {
            return ApiResult.success(exportTemplateService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/copy/{id}", method = RequestMethod.GET)
    public ApiResult<?> copy(@PathVariable() String id) {
        try {
            ExportTemplate template = exportTemplateService.getModel(CLAZZ, id);
            template.setTitle(template.getTitle() + " - 副本");
            template.setId(null);
            return ApiResult.success(exportTemplateService.createModel(template));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<?> createOrUpdate(@RequestBody ExportTemplate form) {
        try {
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                form = exportTemplateService.updateModel(form);
            } else {
                form = exportTemplateService.createModel(form);
            }
            exportTemplateService.generateFile(form.getId(), "template");
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable() String id) {
        try {
            ExportTemplate model = exportTemplateService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            exportTemplateService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generateFile/{id}", method = RequestMethod.POST)
    public ApiResult<?> generateFile(@PathVariable() String id, @RequestBody Map<String, Object> params) {
        try {
            String fileType = (String) params.get("fileType");
            return exportTemplateService.generateFile(id, fileType);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/index/{id}", method = RequestMethod.GET)
    public ApiResult<?> indexTemplate(@PathVariable() String id) {
        Map<Integer, Object> result = new LinkedHashMap<>();
        ExportTemplate exportTemplate = exportTemplateService.getModel(ExportTemplate.class, id);
        if (exportTemplate != null) {
            for (int i = 1; i <= 9; i++) {
                try {
                    Field field = ExportTemplate.class.getDeclaredField("template" + (i == 1 ? "" : i));
                    field.setAccessible(true);
                    Object value = field.get(exportTemplate);
                    if (value != null) {
                        Base64Helper helper = JSON.parseObject(value.toString(), Base64Helper.class);
                        if (helper != null && Strings.isNotBlank(helper.getName())) {
                            result.put(i, helper.getName());
                        }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return ApiResult.success(result);
    }

    @RequestMapping(value = "/base64/{id}/{index}", method = RequestMethod.GET)
    public ApiResult<?> indexTemplate(@PathVariable() String id, @PathVariable() String index) {
        ExportTemplate exportTemplate = exportTemplateService.getModel(ExportTemplate.class, id);
        if (exportTemplate != null) {
            return ApiResult.success(exportTemplate.indexTemplate(index));
        }
        return ApiResult.fail("未找到模板");
    }
}
