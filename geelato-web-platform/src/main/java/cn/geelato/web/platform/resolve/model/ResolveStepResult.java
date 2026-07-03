package cn.geelato.web.platform.resolve.model;

import lombok.Data;

@Data
public class ResolveStepResult {
    private String artifactId;
    private Boolean success;
    private Long costMs;
    private Object output;
    private String errorMsg;
}

