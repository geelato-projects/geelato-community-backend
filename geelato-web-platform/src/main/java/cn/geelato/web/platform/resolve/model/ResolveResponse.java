package cn.geelato.web.platform.resolve.model;

import lombok.Data;

import java.util.List;

@Data
public class ResolveResponse {
    private Object result;
    private List<ResolveStepResult> steps;
}

