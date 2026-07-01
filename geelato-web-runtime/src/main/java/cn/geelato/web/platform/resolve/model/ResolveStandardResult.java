package cn.geelato.web.platform.resolve.model;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class ResolveStandardResult {
    private String biztag;
    private String schemaId;
    private JSONObject data;
    private ExtractedStructuredData extracted;
}
