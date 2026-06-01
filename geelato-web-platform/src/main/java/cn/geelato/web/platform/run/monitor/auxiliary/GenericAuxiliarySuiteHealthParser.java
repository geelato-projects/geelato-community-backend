package cn.geelato.web.platform.run.monitor.auxiliary;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class GenericAuxiliarySuiteHealthParser implements AuxiliarySuiteHealthParser {
    @Override
    public boolean supports(String parserType) {
        return parserType == null || parserType.trim().isEmpty() || "generic".equalsIgnoreCase(parserType);
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

        boolean ok = httpStatus != null && httpStatus >= 200 && httpStatus < 300;
        snapshot.setSuccess(ok);
        snapshot.setRuntimeStatus(ok ? "UP" : "DOWN");
        snapshot.setBusinessStatus(ok ? "UNKNOWN" : "DOWN");

        if (responseBody == null || responseBody.trim().isEmpty()) {
            snapshot.setMessage(ok ? null : "empty response body");
            return snapshot;
        }

        try {
            JSONObject json = JSON.parseObject(responseBody);
            if (json != null) {
                String message = firstNonBlank(
                    json.getString("message"),
                    json.getString("msg"),
                    json.getString("error"),
                    json.getString("detail")
                );
                if (message != null) {
                    snapshot.setMessage(message);
                }
                String runtimeStatus = normalizeStatus(firstNonBlank(
                    json.getString("runtime"),
                    json.getString("status")
                ));
                if (runtimeStatus != null) {
                    snapshot.setRuntimeStatus(runtimeStatus);
                }
                String businessStatus = normalizeStatus(firstNonBlank(
                    json.getString("business"),
                    json.getString("businessStatus")
                ));
                if (businessStatus != null) {
                    snapshot.setBusinessStatus(businessStatus);
                }
                Boolean success = json.getBoolean("success");
                if (success != null) {
                    snapshot.setSuccess(success);
                }
            }
        } catch (Exception ignored) {
            // Non-JSON responses are allowed for generic parser.
        }

        return snapshot;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
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
