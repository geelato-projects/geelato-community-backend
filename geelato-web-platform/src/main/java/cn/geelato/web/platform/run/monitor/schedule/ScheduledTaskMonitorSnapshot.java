package cn.geelato.web.platform.run.monitor.schedule;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduledTaskMonitorSnapshot {
    private String taskId;
    private String beanName;
    private String className;
    private String simpleClassName;
    private String methodName;
    private String moduleName;
    private Boolean registeredInContext = false;
    private String conditionalProperty;
    private String conditionalHavingValue;
    private Boolean conditionalMatchIfMissing;
    private String triggerTypeSummary;
    private List<ScheduledTaskMonitorTrigger> triggers = new ArrayList<>();
    private String runtimeStatus;
    private Boolean running = false;
    private Long runCount = 0L;
    private Long successCount = 0L;
    private Long failureCount = 0L;
    private Long lastStartedAt;
    private Long lastFinishedAt;
    private Long lastDurationMs;
    private Boolean lastSuccess;
    private String lastErrorMessage;
    private Long nextRunAt;
    private String lastThreadName;

    public ScheduledTaskMonitorSnapshot copy() {
        ScheduledTaskMonitorSnapshot copy = new ScheduledTaskMonitorSnapshot();
        copy.setTaskId(taskId);
        copy.setBeanName(beanName);
        copy.setClassName(className);
        copy.setSimpleClassName(simpleClassName);
        copy.setMethodName(methodName);
        copy.setModuleName(moduleName);
        copy.setRegisteredInContext(registeredInContext);
        copy.setConditionalProperty(conditionalProperty);
        copy.setConditionalHavingValue(conditionalHavingValue);
        copy.setConditionalMatchIfMissing(conditionalMatchIfMissing);
        copy.setTriggerTypeSummary(triggerTypeSummary);
        copy.setTriggers(new ArrayList<>(triggers));
        copy.setRuntimeStatus(runtimeStatus);
        copy.setRunning(running);
        copy.setRunCount(runCount);
        copy.setSuccessCount(successCount);
        copy.setFailureCount(failureCount);
        copy.setLastStartedAt(lastStartedAt);
        copy.setLastFinishedAt(lastFinishedAt);
        copy.setLastDurationMs(lastDurationMs);
        copy.setLastSuccess(lastSuccess);
        copy.setLastErrorMessage(lastErrorMessage);
        copy.setNextRunAt(nextRunAt);
        copy.setLastThreadName(lastThreadName);
        return copy;
    }
}
