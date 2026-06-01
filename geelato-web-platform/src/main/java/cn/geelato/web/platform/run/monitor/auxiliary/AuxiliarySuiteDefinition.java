package cn.geelato.web.platform.run.monitor.auxiliary;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AuxiliarySuiteDefinition {
    private String code;
    private String name;
    private String healthUrl;
    private String parserType = "generic";
    private Boolean enabled = true;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private String remark;
}
