package cn.geelato.web.platform.run.monitor.auxiliary;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuxiliarySuiteHealthSnapshot {
    private String code;
    private String name;
    private String parserType;
    private String healthUrl;
    private Boolean enabled = true;
    private Integer httpStatus;
    private Boolean success = false;
    private String runtimeStatus = "UNKNOWN";
    private String businessStatus = "UNKNOWN";
    private Long checkedAt;
    private Long durationMs;
    private String message;
    private String rawBody;
    private List<AuxiliarySuiteHealthModule> modules = new ArrayList<>();
}
