package cn.geelato.web.platform.run.monitor.schedule;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduledTaskMonitorSummary {
    private Long scannedAt;
    private Integer taskCount = 0;
    private Integer activeCount = 0;
    private Integer inactiveCount = 0;
    private Integer runningCount = 0;
    private Integer successCount = 0;
    private Integer failedCount = 0;
    private Integer neverRunCount = 0;
    private String lastError;
    private List<ScheduledTaskMonitorSnapshot> tasks = new ArrayList<>();
}
