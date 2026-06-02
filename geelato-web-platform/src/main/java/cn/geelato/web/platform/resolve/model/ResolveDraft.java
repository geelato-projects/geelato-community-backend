package cn.geelato.web.platform.resolve.model;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class ResolveDraft {
    private String draftId;
    private ResolveDraftStatusEnum status;
    private String biztag;
    private String fileId;
    private String fileName;
    private JSONObject config;
    private ExtractedStructuredData extracted;
    private List<MappingSuggestion> mapping;
    private List<ResolveStepResult> steps;
    private Object persistResult;
    private String errorMsg;
    private Long createdAt;
    private Long updatedAt;
}
