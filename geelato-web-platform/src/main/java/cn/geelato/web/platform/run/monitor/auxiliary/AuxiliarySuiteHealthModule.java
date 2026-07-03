package cn.geelato.web.platform.run.monitor.auxiliary;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuxiliarySuiteHealthModule {
    private String module;
    private String runtimeStatus;
    private String businessStatus;
    private Long updatedAt;
    private String message;
    private List<AuxiliarySuiteHealthItem> items = new ArrayList<>();
}
