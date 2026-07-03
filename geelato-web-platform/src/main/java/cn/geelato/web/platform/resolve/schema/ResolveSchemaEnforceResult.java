package cn.geelato.web.platform.resolve.schema;

import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResolveSchemaEnforceResult {
    private boolean success;
    private String schemaId;
    private JSONObject normalizedData;
    private ExtractedStructuredData normalizedExtracted;
    private int fixedCount;
    private final List<String> errors = new ArrayList<>();
}
