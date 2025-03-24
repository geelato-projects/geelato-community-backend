package cn.geelato.web.platform.m.ocr.rest;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import cn.geelato.web.platform.m.arco.entity.SelectOptionGroup;
import cn.geelato.web.platform.m.ocr.entity.OcrPdf;
import cn.geelato.web.platform.m.ocr.service.OcrPdfService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApiRestController("/ocr/pdf")
@Slf4j
public class OcrPdfController extends BaseController {
    private static final Class<OcrPdf> CLAZZ = OcrPdf.class;
    private final OcrPdfService ocrPdfService;
    PluginBeanProvider pluginBeanProvider;

    @Autowired
    public OcrPdfController(OcrPdfService ocrPdfService, PluginBeanProvider pluginBeanProvider) {
        this.ocrPdfService = ocrPdfService;
        this.pluginBeanProvider = pluginBeanProvider;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return ocrPdfService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(ocrPdfService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id, Boolean hasMeta) {
        try {
            return ApiResult.success(ocrPdfService.getModel(id, hasMeta));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody OcrPdf form, Boolean hasMeta) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                form = ocrPdfService.updateModel(form, hasMeta);
            } else {
                form = ocrPdfService.createModel(form, hasMeta);
            }
            return ApiResult.success(form);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            OcrPdf model = ocrPdfService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            ocrPdfService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/select/group", method = RequestMethod.GET)
    public ApiResult<?> querySelectOptions() {
        String sql = "SELECT p2.id pid, p2.title, p1.id, p1.`name` FROM platform_ocr_pdf_meta p1 " +
                "LEFT JOIN platform_ocr_pdf p2 ON p2.id = p1.pdf_id " +
                "WHERE 1=1 " +
                "AND p2.del_status = 0 AND p1.del_status = 0 " +
                "AND p2.enable_status = 1 " +
                "AND p2.title IS NOT NULL AND p2.title != '' " +
                "AND p1.rule IS NOT NULL AND p1.rule != '' " +
                "AND p1.`name` IS NOT NULL AND p1.`name` != '' " +
                "ORDER BY p2.update_at DESC,p1.seq_no ASC;";
        List<Map<String, Object>> list = dao.getJdbcTemplate().queryForList(sql);
        if (list == null || list.isEmpty()) {
            return ApiResult.successNoResult();
        }
        List<String> pIds = list.stream()
                .map(map -> map.get("pid") == null ? null : map.get("pid").toString())
                .filter(id -> Strings.isNotBlank(id))
                .distinct()
                .collect(Collectors.toList());
        if (pIds.isEmpty()) {
            return ApiResult.successNoResult();
        }
        List<SelectOptionGroup> groups = new ArrayList<>();
        for (String pid : pIds) {
            SelectOptionGroup group = new SelectOptionGroup();
            group.setIsGroup(true);
            List<SelectOptionData> options = new ArrayList<>();
            for (Map<String, Object> map : list) {
                if (pid.equals(map.get("pid"))) {
                    String title = map.get("title") != null ? map.get("title").toString() : "";
                    String id = map.get("id") != null ? map.get("id").toString() : "";
                    String name = map.get("name") != null ? map.get("name").toString() : "";
                    if (Strings.isNotBlank(title) && Strings.isNotBlank(id) && Strings.isNotBlank(name)) {
                        SelectOptionData option = new SelectOptionData();
                        group.setLabel(title);
                        option.setValue(id);
                        option.setLabel(name);
                        options.add(option);
                    }
                }
            }
            group.setOptions(options.toArray(new SelectOptionData[0]));
            groups.add(group);
        }
        return ApiResult.success(groups);
    }
}
