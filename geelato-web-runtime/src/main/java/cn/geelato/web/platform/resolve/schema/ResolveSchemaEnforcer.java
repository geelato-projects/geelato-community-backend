package cn.geelato.web.platform.resolve.schema;

import cn.geelato.web.platform.resolve.model.ExtractedField;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class ResolveSchemaEnforcer {
    private final ResolveSchemaRegistry schemaRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResolveSchemaEnforcer(ResolveSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * 按 biztag 对 extracted 执行结构修正与 schema 校验，并产出标准化 data。
     */
    public ResolveSchemaEnforceResult enforce(String biztag, ExtractedStructuredData extracted) {
        ResolveSchemaEnforceResult result = new ResolveSchemaEnforceResult();
        ResolveSchema schema = schemaRegistry.get(biztag);
        if (schema == null) {
            result.setSuccess(true);
            result.setSchemaId(null);
            result.setNormalizedExtracted(extracted);
            result.setNormalizedData(buildDataFromExtracted(extracted, result));
            if (log.isDebugEnabled()) {
                log.debug("Resolve schema skipped: biztag={}, fieldCount={}",
                        biztag,
                        extracted == null || extracted.getFields() == null ? 0 : extracted.getFields().size());
            }
            return result;
        }
        result.setSchemaId(schema.getSchemaId());

        ExtractedStructuredData normalizedExtracted = normalizeExtracted(extracted, result);
        JSONObject data = buildDataFromExtracted(normalizedExtracted, result);

        JSONObject standardResult = new JSONObject();
        standardResult.put("biztag", biztag);
        standardResult.put("schemaId", schema.getSchemaId());
        standardResult.put("data", data);
        standardResult.put("extracted", JSON.parseObject(JSON.toJSONString(normalizedExtracted)));

        try {
            JsonNode node = objectMapper.readTree(standardResult.toJSONString());
            Set<ValidationMessage> errors = schema.getSchema().validate(node);
            if (errors != null && !errors.isEmpty()) {
                for (ValidationMessage vm : errors) {
                    if (vm != null && Strings.isNotBlank(vm.getMessage())) {
                        result.getErrors().add(vm.getMessage());
                    }
                }
                result.setSuccess(false);
            } else {
                result.setSuccess(true);
            }
        } catch (Exception e) {
            result.getErrors().add(e.getMessage());
            result.setSuccess(false);
            log.debug("Resolve schema validation exception: biztag={}, error={}", biztag, e.getMessage());
        }

        result.setNormalizedExtracted(normalizedExtracted);
        result.setNormalizedData(data);
        if (log.isDebugEnabled()) {
            log.debug("Resolve schema enforced: biztag={}, schemaId={}, success={}, fixedCount={}, errorCount={}",
                    biztag,
                    result.getSchemaId(),
                    result.isSuccess(),
                    result.getFixedCount(),
                    result.getErrors() == null ? 0 : result.getErrors().size());
        }
        return result;
    }

    private ExtractedStructuredData normalizeExtracted(ExtractedStructuredData extracted, ResolveSchemaEnforceResult result) {
        ExtractedStructuredData data = extracted;
        if (data == null) {
            data = new ExtractedStructuredData();
            data.setFields(new ArrayList<>());
            result.setFixedCount(result.getFixedCount() + 1);
            return data;
        }
        if (data.getFields() == null) {
            data.setFields(new ArrayList<>());
            result.setFixedCount(result.getFixedCount() + 1);
            return data;
        }
        List<ExtractedField> filtered = new ArrayList<>();
        for (ExtractedField f : data.getFields()) {
            if (f == null || Strings.isBlank(f.getKey())) {
                result.setFixedCount(result.getFixedCount() + 1);
                continue;
            }
            filtered.add(f);
        }
        data.setFields(filtered);
        return data;
    }

    private JSONObject buildDataFromExtracted(ExtractedStructuredData extracted, ResolveSchemaEnforceResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (extracted != null && extracted.getFields() != null) {
            for (ExtractedField f : extracted.getFields()) {
                if (f == null || Strings.isBlank(f.getKey())) {
                    continue;
                }
                Object v = coerceValue(f.getValue(), result);
                map.put(f.getKey(), v);
            }
        }
        return new JSONObject(map);
    }

    private Object coerceValue(String value, ResolveSchemaEnforceResult result) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            if (!value.isEmpty()) {
                result.setFixedCount(result.getFixedCount() + 1);
            }
            return "";
        }
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            result.setFixedCount(result.getFixedCount() + 1);
            return Boolean.parseBoolean(s);
        }
        if (s.matches("^-?\\d+$")) {
            try {
                result.setFixedCount(result.getFixedCount() + 1);
                return Long.parseLong(s);
            } catch (Exception ignored) {
            }
        }
        if (s.matches("^-?\\d+\\.\\d+$")) {
            try {
                result.setFixedCount(result.getFixedCount() + 1);
                return Double.parseDouble(s);
            } catch (Exception ignored) {
            }
        }
        if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"))) {
            try {
                Object parsed = JSON.parse(s);
                result.setFixedCount(result.getFixedCount() + 1);
                return parsed;
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}
