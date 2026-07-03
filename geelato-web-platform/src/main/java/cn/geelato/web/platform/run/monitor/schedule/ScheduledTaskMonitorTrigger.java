package cn.geelato.web.platform.run.monitor.schedule;

import lombok.Data;

@Data
public class ScheduledTaskMonitorTrigger {
    private String triggerType;
    private String cron;
    private Long fixedDelay;
    private String fixedDelayString;
    private Long fixedRate;
    private String fixedRateString;
    private Long initialDelay;
    private String initialDelayString;
    private String zone;
    private String scheduler;
    private String timeUnit;
}
