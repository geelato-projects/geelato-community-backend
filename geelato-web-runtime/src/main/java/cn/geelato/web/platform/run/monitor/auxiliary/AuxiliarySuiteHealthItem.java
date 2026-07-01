package cn.geelato.web.platform.run.monitor.auxiliary;

import lombok.Data;

@Data
public class AuxiliarySuiteHealthItem {
    private String name;
    private String level;
    private Long updatedAt;
    private Long latencyMs;
    private String message;
}
