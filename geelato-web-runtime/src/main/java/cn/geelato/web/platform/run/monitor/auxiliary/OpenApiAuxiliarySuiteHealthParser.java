package cn.geelato.web.platform.run.monitor.auxiliary;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class OpenApiAuxiliarySuiteHealthParser implements AuxiliarySuiteHealthParser {
    @Override
    public boolean supports(String parserType) {
        return "openapi".equalsIgnoreCase(parserType);
    }

    @Override
    public AuxiliarySuiteHealthSnapshot parse(AuxiliarySuiteDefinition definition, Integer httpStatus, String responseBody, long checkedAt, long durationMs) {
        AuxiliarySuiteHealthSnapshot snapshot = new AuxiliarySuiteHealthSnapshot();
        snapshot.setCode(definition.getCode());
        snapshot.setName(definition.getName());
        snapshot.setParserType(definition.getParserType());
        snapshot.setHealthUrl(definition.getHealthUrl());
        snapshot.setEnabled(Boolean.TRUE.equals(definition.getEnabled()));
        snapshot.setHttpStatus(httpStatus);
        snapshot.setCheckedAt(checkedAt);
        snapshot.setDurationMs(durationMs);
        snapshot.setRawBody(responseBody);

        if (responseBody == null || responseBody.trim().isEmpty()) {
            snapshot.setSuccess(false);
            snapshot.setRuntimeStatus("DOWN");
            snapshot.setBusinessStatus("DOWN");
            snapshot.setMessage("empty response body");
            return snapshot;
        }

        try {
            JSONObject root = JSON.parseObject(responseBody);
            String runtime = normalizeStatus(root.getString("runtime"));
            String business = normalizeStatus(root.getString("business"));
            snapshot.setRuntimeStatus(runtime == null ? "UNKNOWN" : runtime);
            snapshot.setBusinessStatus(business == null ? "UNKNOWN" : business);
            snapshot.setSuccess("UP".equals(snapshot.getRuntimeStatus()) && !"DOWN".equals(snapshot.getBusinessStatus()));

            JSONArray modules = root.getJSONArray("modules");
            if (modules != null) {
                for (int i = 0; i < modules.size(); i++) {
                    JSONObject moduleJson = modules.getJSONObject(i);
                    if (moduleJson == null) {
                        continue;
                    }
                    AuxiliarySuiteHealthModule module = new AuxiliarySuiteHealthModule();
                    module.setModule(moduleJson.getString("module"));
                    module.setRuntimeStatus(normalizeStatus(moduleJson.getString("runtime")));
                    module.setBusinessStatus(normalizeStatus(moduleJson.getString("business")));
                    module.setUpdatedAt(firstPositive(moduleJson.getLong("runtimeUpdatedAt"), moduleJson.getLong("businessUpdatedAt")));
                    module.setMessage(extractModuleMessage(moduleJson));

                    JSONArray items = moduleJson.getJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < items.size(); j++) {
                            JSONObject itemJson = items.getJSONObject(j);
                            if (itemJson == null) {
                                continue;
                            }
                            AuxiliarySuiteHealthItem item = new AuxiliarySuiteHealthItem();
                            item.setName(itemJson.getString("name"));
                            item.setLevel(normalizeStatus(itemJson.getString("level")));
                            item.setUpdatedAt(itemJson.getLong("updatedAt"));
                            item.setLatencyMs(itemJson.getLong("latencyMs"));
                            item.setMessage(itemJson.getString("message"));
                            module.getItems().add(item);
                        }
                    }
                    snapshot.getModules().add(module);
                }
            }
        } catch (Exception e) {
            snapshot.setSuccess(false);
            snapshot.setRuntimeStatus("DOWN");
            snapshot.setBusinessStatus("DOWN");
            snapshot.setMessage("parse openapi health response failed: " + e.getMessage());
        }

        return snapshot;
    }

    private String extractModuleMessage(JSONObject moduleJson) {
        JSONArray items = moduleJson.getJSONArray("items");
        if (items == null) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String msg = item.getString("message");
            if (msg != null && !msg.trim().isEmpty()) {
                return msg.trim();
            }
        }
        return null;
    }

    private Long firstPositive(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if ("HEALTH".equals(upper) || "HEALTHY".equals(upper) || "OK".equals(upper)) {
            return "UP";
        }
        return upper;
    }
}
