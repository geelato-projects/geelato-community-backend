package cn.geelato.web.platform.run.monitor.schedule;

import lombok.Data;

@Data
public class ScheduledTaskRuntimeState {
    private Boolean running = false;
    private Long runCount = 0L;
    private Long successCount = 0L;
    private Long failureCount = 0L;
    private Long lastStartedAt;
    private Long lastFinishedAt;
    private Long lastDurationMs;
    private Boolean lastSuccess;
    private String lastErrorMessage;
    private String lastThreadName;
}
