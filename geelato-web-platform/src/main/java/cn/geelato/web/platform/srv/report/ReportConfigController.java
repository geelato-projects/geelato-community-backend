package cn.geelato.web.platform.srv.report;

import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.platform.srv.report.dto.ReportApiResponse;
import cn.geelato.web.platform.srv.report.dto.ReportConfigDetailData;
import cn.geelato.web.platform.srv.report.dto.ReportConfigSaveRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustResult;
import cn.geelato.web.platform.srv.report.service.ReportConfigEditorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@DesignTimeApiRestController(value = "/periodic-report/report-config", category = "platform-design")
@RequiredArgsConstructor
@Slf4j
public class ReportConfigController {
    private final ReportConfigEditorService reportConfigEditorService;

    @GetMapping("/detail")
    public ReportApiResponse<ReportConfigDetailData> detail(@RequestParam(value = "templateId", required = false) String templateId,
                                                            @RequestParam(value = "reportTypeId", required = false) String reportTypeId,
                                                            @RequestParam("customerId") String customerId) {
        try {
            return ReportApiResponse.success(reportConfigEditorService.getEditorDetail(templateId, reportTypeId, customerId));
        } catch (Exception e) {
            log.error("load report config detail failed", e);
            return ReportApiResponse.error(e.getMessage());
        }
    }

    @PostMapping(value = "/save", consumes = MediaTypes.APPLICATION_JSON_UTF_8)
    public ReportApiResponse<ReportConfigDetailData> save(@RequestBody ReportConfigSaveRequest request) {
        try {
            return ReportApiResponse.success(reportConfigEditorService.saveEditorDetail(request));
        } catch (Exception e) {
            log.error("save report config failed", e);
            return ReportApiResponse.error(e.getMessage());
        }
    }

    @PostMapping(value = "/adjust-template", consumes = MediaTypes.APPLICATION_JSON_UTF_8)
    public ReportApiResponse<TemplateAdjustResult> adjustTemplate(@RequestBody TemplateAdjustRequest request) {
        try {
            return ReportApiResponse.success(reportConfigEditorService.adjustTemplate(request));
        } catch (Exception e) {
            log.error("adjust template failed", e);
            return ReportApiResponse.error(e.getMessage());
        }
    }
}
