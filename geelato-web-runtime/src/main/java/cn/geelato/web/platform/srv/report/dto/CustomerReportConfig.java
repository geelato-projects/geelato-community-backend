package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class CustomerReportConfig {
    private String id;
    private String reportTypeId;
    private String configName;
    private String scopeType;
    private String targetType;
    private String targetId;
    private String templateName;
    private String templateEngine;
    private String templateContent;
    private String templateSchema;
    private String sourceTemplateId;
    private String llmPromptConfig;
    private String scheduleType;
    private String scheduleExpr;
    private String timezone;
    private String channels;
    private String recipientRuleJson;
    private String bizFilterJson;
    private boolean enabled;
    private Timestamp lastDispatchAt;
    private String tenantCode;
}
