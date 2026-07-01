package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TemplateMaterial {
    private String id;
    private String reportTypeId;
    private String templateCode;
    private String templateName;
    private String templateKind;
    private String templateEngine;
    private String templateContent;
    private String templateSchema;
    private String sourceTemplateId;
    private String customerId;
    private String status;
    private String llmPromptConfig;
    private String tenantCode;
}
