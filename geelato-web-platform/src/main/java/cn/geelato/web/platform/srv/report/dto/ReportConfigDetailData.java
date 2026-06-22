package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ReportConfigDetailData {
    private List<ReportTypeConfig> reportTypes = new ArrayList<>();
    private TemplateMaterial currentTemplate;
    private CustomerReportConfig customerConfig;
}
