package cn.geelato.web.platform.srv.report.service;

import cn.geelato.web.platform.srv.report.dto.ReportConfigDetailData;
import cn.geelato.web.platform.srv.report.dto.ReportConfigSaveRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustRequest;
import cn.geelato.web.platform.srv.report.dto.TemplateAdjustResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ReportConfigEditorService {
    private final ReportConfigEditorDatabaseService databaseService;
    private final TemplateAdjustService templateAdjustService;

    public ReportConfigDetailData getEditorDetail(String templateId, String reportTypeId, String customerId) {
        return databaseService.loadDetail(templateId, reportTypeId, customerId);
    }

    public ReportConfigDetailData saveEditorDetail(ReportConfigSaveRequest request) {
        return databaseService.saveDetail(request);
    }

    public TemplateAdjustResult adjustTemplate(TemplateAdjustRequest request) throws IOException {
        return templateAdjustService.adjustTemplate(request);
    }
}
