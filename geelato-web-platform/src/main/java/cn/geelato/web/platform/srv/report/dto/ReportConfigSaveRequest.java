package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportConfigSaveRequest {
    private TemplateMaterial template;
    private CustomerReportConfig customerConfig;
}
