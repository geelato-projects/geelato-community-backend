package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TemplateAdjustRequest {
    private String templateContent;
    private String templateSchema;
    private String feedback;
}
