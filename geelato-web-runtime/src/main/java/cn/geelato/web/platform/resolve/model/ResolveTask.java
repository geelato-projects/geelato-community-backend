package cn.geelato.web.platform.resolve.model;

import lombok.Data;

import java.util.List;

@Data
public class ResolveTask {
    private String taskId;
    private ResolveStatusEnum status;
    private String biztag;
    private String feature;
    private String fileName;
    private Long createdAt;
    private Long updatedAt;
    private List<ResolveStepResult> steps;
    private Object result;
    private String errorMsg;
}
