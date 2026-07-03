package cn.geelato.web.platform.run.monitor.schedule;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScheduledTaskRuntimeTracker {
    private final Map<String, ScheduledTaskRuntimeState> stateMap = new ConcurrentHashMap<>();

    public ScheduledTaskRuntimeState getState(String taskId) {
        return taskId == null ? null : stateMap.get(taskId);
    }

    public void markStart(String taskId, String threadName, long startedAt) {
        if (taskId == null) {
            return;
        }
        ScheduledTaskRuntimeState state = stateMap.computeIfAbsent(taskId, key -> new ScheduledTaskRuntimeState());
        synchronized (state) {
            state.setRunning(true);
            state.setLastStartedAt(startedAt);
            state.setLastThreadName(threadName);
        }
    }

    public void markSuccess(String taskId, long finishedAt, long durationMs) {
        if (taskId == null) {
            return;
        }
        ScheduledTaskRuntimeState state = stateMap.computeIfAbsent(taskId, key -> new ScheduledTaskRuntimeState());
        synchronized (state) {
            state.setRunning(false);
            state.setLastFinishedAt(finishedAt);
            state.setLastDurationMs(durationMs);
            state.setLastSuccess(true);
            state.setLastErrorMessage(null);
            state.setRunCount(defaultZero(state.getRunCount()) + 1L);
            state.setSuccessCount(defaultZero(state.getSuccessCount()) + 1L);
        }
    }

    public void markFailure(String taskId, long finishedAt, long durationMs, Throwable throwable) {
        if (taskId == null) {
            return;
        }
        ScheduledTaskRuntimeState state = stateMap.computeIfAbsent(taskId, key -> new ScheduledTaskRuntimeState());
        synchronized (state) {
            state.setRunning(false);
            state.setLastFinishedAt(finishedAt);
            state.setLastDurationMs(durationMs);
            state.setLastSuccess(false);
            state.setLastErrorMessage(throwable == null ? null : throwable.getMessage());
            state.setRunCount(defaultZero(state.getRunCount()) + 1L);
            state.setFailureCount(defaultZero(state.getFailureCount()) + 1L);
        }
    }

    public void retainOnly(Set<String> taskIds) {
        if (taskIds == null) {
            stateMap.clear();
            return;
        }
        stateMap.keySet().removeIf(key -> !taskIds.contains(key));
    }

    private long defaultZero(Long value) {
        return value == null ? 0L : value;
    }
}
