package cn.geelato.web.platform.srv.report.service;

import cn.geelato.web.platform.srv.report.dto.ReportConfigDetailData;
import cn.geelato.web.platform.srv.report.dto.ReportConfigSaveRequest;
import cn.geelato.web.platform.srv.report.dto.ReportTypeConfig;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustResult;
import cn.geelato.web.platform.srv.report.dto.TemplateReflowRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportConfigEditorService {
    private final ReportConfigEditorDatabaseService databaseService;
    private final TemplateAdjustService templateAdjustService;

    public ReportConfigDetailData getEditorDetail(String templateId, String reportTypeId, String customerId) {
        return databaseService.loadDetail(templateId, reportTypeId, customerId);
    }

    public List<ReportTypeConfig> listReportTypes() {
        return databaseService.listAllReportTypes();
    }

    public ReportConfigDetailData saveEditorDetail(ReportConfigSaveRequest request) {
        return databaseService.saveDetail(request);
    }

    public TemplateAdjustResult adjustTemplate(TemplateAdjustRequest request) throws IOException {
        return templateAdjustService.adjustTemplate(request);
    }

    public TemplateAdjustResult reflowTemplate(TemplateReflowRequest request) throws IOException {
        return templateAdjustService.reflowTemplate(request);
    }
}
