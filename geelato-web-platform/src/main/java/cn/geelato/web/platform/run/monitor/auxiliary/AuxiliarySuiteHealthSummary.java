package cn.geelato.web.platform.run.monitor.auxiliary;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuxiliarySuiteHealthSummary {
    private Long checkedAt;
    private Integer pollIntervalSeconds;
    private Integer suiteCount = 0;
    private Integer healthyCount = 0;
    private Integer abnormalCount = 0;
    private Integer unknownCount = 0;
    private Boolean hasFailure = false;
    private String lastError;
    private List<AuxiliarySuiteHealthSnapshot> suites = new ArrayList<>();
}
