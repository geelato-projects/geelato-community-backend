package cn.geelato.web.platform.resolve.core;

import cn.geelato.web.platform.resolve.model.ResolveStepResult;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ResolveContext {
    private String taskId;
    private File sourceFile;
    private String sourceFileName;
    private String sourceExt;
    private String appId;
    private String tenantCode;

    private JSONObject payload;
    private String biztag;
    private String feature;
    private JSONObject params;
    private JSONObject workflow;
    private JSONObject trace;

    private final Map<String, Object> artifactsData = new HashMap<>();
    private final List<ResolveStepResult> steps = new ArrayList<>();

    private Object result;
    private final List<File> tempFiles = new ArrayList<>();

    public void putArtifactData(String key, Object value) {
        artifactsData.put(key, value);
    }

    public Object getArtifactData(String key) {
        return artifactsData.get(key);
    }
}
