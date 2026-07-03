package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportTypeConfig {
    private String id;
    private String reportCode;
    private String reportName;
    private String scopeType;
    private String dataProviderCode;
    private String rendererType;
    private boolean enabled;
    private String tenantCode;
}
