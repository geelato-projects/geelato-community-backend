package cn.geelato.web.platform.run.monitor.schedule;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ScheduledTaskMonitorRegistry {
    private static final String BASE_PACKAGE = "cn.geelato";

    private final ApplicationContext applicationContext;
    private final ScheduledTaskRuntimeTracker runtimeTracker;
    private final AtomicReference<List<ScheduledTaskMonitorSnapshot>> definitionRef =
        new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<String> lastErrorRef = new AtomicReference<>(null);
    private final AtomicLong scannedAtRef = new AtomicLong(0L);
    private final long applicationStartedAt = System.currentTimeMillis();

    public ScheduledTaskMonitorRegistry(ApplicationContext applicationContext,
                                        ScheduledTaskRuntimeTracker runtimeTracker) {
        this.applicationContext = applicationContext;
        this.runtimeTracker = runtimeTracker;
    }

    @PostConstruct
    public void init() {
        scanNow();
    }

    public synchronized ScheduledTaskMonitorSummary scanNow() {
        try {
            List<ScheduledTaskMonitorSnapshot> definitions = scanDefinitions();
            definitionRef.set(definitions);
            scannedAtRef.set(System.currentTimeMillis());
            lastErrorRef.set(null);
            runtimeTracker.retainOnly(definitions.stream()
                .map(ScheduledTaskMonitorSnapshot::getTaskId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        } catch (Exception e) {
            log.error("scan scheduled task monitor definitions failed", e);
            lastErrorRef.set(e.getMessage());
        }
        return getSummary();
    }

    public ScheduledTaskMonitorSummary getSummary() {
        List<ScheduledTaskMonitorSnapshot> definitions = definitionRef.get();
        ScheduledTaskMonitorSummary summary = new ScheduledTaskMonitorSummary();
        summary.setScannedAt(scannedAtRef.get() > 0 ? scannedAtRef.get() : null);
        summary.setLastError(lastErrorRef.get());
        for (ScheduledTaskMonitorSnapshot definition : definitions) {
            ScheduledTaskMonitorSnapshot snapshot = definition.copy();
            ScheduledTaskRuntimeState state = runtimeTracker.getState(snapshot.getTaskId());
            applyRuntimeState(snapshot, state);
            snapshot.setNextRunAt(resolveNextRunAt(snapshot, state));
            summary.getTasks().add(snapshot);
            summary.setTaskCount(summary.getTaskCount() + 1);
            if (Boolean.TRUE.equals(snapshot.getRegisteredInContext())) {
                summary.setActiveCount(summary.getActiveCount() + 1);
            } else {
                summary.setInactiveCount(summary.getInactiveCount() + 1);
            }
            if ("RUNNING".equals(snapshot.getRuntimeStatus())) {
                summary.setRunningCount(summary.getRunningCount() + 1);
            } else if ("SUCCESS".equals(snapshot.getRuntimeStatus())) {
                summary.setSuccessCount(summary.getSuccessCount() + 1);
            } else if ("FAILED".equals(snapshot.getRuntimeStatus())) {
                summary.setFailedCount(summary.getFailedCount() + 1);
            } else if ("NEVER_RUN".equals(snapshot.getRuntimeStatus())) {
                summary.setNeverRunCount(summary.getNeverRunCount() + 1);
            }
        }
        return summary;
    }

    public static String buildTaskId(Class<?> targetClass, Method method) {
        return (targetClass == null ? "unknown" : targetClass.getName()) + "#" + method.getName();
    }

    private List<ScheduledTaskMonitorSnapshot> scanDefinitions() throws Exception {
        List<ScheduledTaskMonitorSnapshot> definitions = new ArrayList<>();
        Map<Class<?>, List<String>> beanNameMap = buildBeanNameMap();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = PathMatchingResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + ClassUtils.convertClassNameToResourcePath(BASE_PACKAGE) + "/**/*.class";
        Resource[] resources = resolver.getResources(pattern);
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            MetadataReader metadataReader = new CachingMetadataReaderFactory(resolver).getMetadataReader(resource);
            Class<?> candidateClass = ClassUtils.forName(metadataReader.getClassMetadata().getClassName(), null);
            if (!isSpringComponent(candidateClass)) {
                continue;
            }
            collectMethodDefinitions(candidateClass, beanNameMap, definitions);
        }
        definitions.sort(Comparator
            .comparing(ScheduledTaskMonitorSnapshot::getModuleName, Comparator.nullsLast(String::compareTo))
            .thenComparing(ScheduledTaskMonitorSnapshot::getClassName, Comparator.nullsLast(String::compareTo))
            .thenComparing(ScheduledTaskMonitorSnapshot::getMethodName, Comparator.nullsLast(String::compareTo)));
        return definitions;
    }

    private boolean isSpringComponent(Class<?> candidateClass) {
        if (candidateClass == null) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(candidateClass, Component.class);
    }

    private void collectMethodDefinitions(Class<?> candidateClass,
                                          Map<Class<?>, List<String>> beanNameMap,
                                          List<ScheduledTaskMonitorSnapshot> definitions) {
        Method[] methods = candidateClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            Collection<Scheduled> scheduledAnnotations =
                AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class);
            if (scheduledAnnotations == null || scheduledAnnotations.isEmpty()) {
                continue;
            }
            ScheduledTaskMonitorSnapshot snapshot = new ScheduledTaskMonitorSnapshot();
            snapshot.setTaskId(buildTaskId(candidateClass, method));
            snapshot.setClassName(candidateClass.getName());
            snapshot.setSimpleClassName(candidateClass.getSimpleName());
            snapshot.setMethodName(method.getName());
            snapshot.setModuleName(resolveModuleName(candidateClass));
            List<String> beanNames = resolveBeanNames(candidateClass, beanNameMap);
            snapshot.setRegisteredInContext(!beanNames.isEmpty());
            snapshot.setBeanName(beanNames.isEmpty() ? null : String.join(",", beanNames));
            fillConditionalInfo(snapshot, candidateClass);
            fillTriggerInfo(snapshot, scheduledAnnotations);
            definitions.add(snapshot);
        }
    }

    private Map<Class<?>, List<String>> buildBeanNameMap() {
        Map<Class<?>, List<String>> beanNameMap = new ConcurrentHashMap<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            try {
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType == null) {
                    continue;
                }
                Class<?> userClass = ClassUtils.getUserClass(beanType);
                beanNameMap.computeIfAbsent(userClass, key -> new ArrayList<>()).add(beanName);
            } catch (Exception ignored) {
                // ignore beans that cannot expose concrete type safely
            }
        }
        return beanNameMap;
    }

    private List<String> resolveBeanNames(Class<?> candidateClass, Map<Class<?>, List<String>> beanNameMap) {
        List<String> beanNames = new ArrayList<>();
        for (Map.Entry<Class<?>, List<String>> entry : beanNameMap.entrySet()) {
            Class<?> beanType = entry.getKey();
            if (beanType == null) {
                continue;
            }
            if (candidateClass.isAssignableFrom(beanType) || beanType.isAssignableFrom(candidateClass)) {
                beanNames.addAll(entry.getValue());
            }
        }
        return beanNames.stream().distinct().collect(Collectors.toList());
    }

    private void fillConditionalInfo(ScheduledTaskMonitorSnapshot snapshot, Class<?> candidateClass) {
        ConditionalOnProperty conditionalOnProperty =
            AnnotatedElementUtils.findMergedAnnotation(candidateClass, ConditionalOnProperty.class);
        if (conditionalOnProperty == null) {
            return;
        }
        String[] names = conditionalOnProperty.name();
        if (names == null || names.length == 0) {
            names = conditionalOnProperty.value();
        }
        if (names != null && names.length > 0) {
            snapshot.setConditionalProperty(String.join(",", names));
        }
        if (StringUtils.hasText(conditionalOnProperty.havingValue())) {
            snapshot.setConditionalHavingValue(conditionalOnProperty.havingValue());
        }
        snapshot.setConditionalMatchIfMissing(conditionalOnProperty.matchIfMissing());
    }

    private void fillTriggerInfo(ScheduledTaskMonitorSnapshot snapshot, Collection<Scheduled> scheduledAnnotations) {
        Set<String> triggerTypes = new LinkedHashSet<>();
        for (Scheduled scheduled : scheduledAnnotations) {
            ScheduledTaskMonitorTrigger trigger = buildTrigger(scheduled);
            snapshot.getTriggers().add(trigger);
            if (StringUtils.hasText(trigger.getTriggerType())) {
                triggerTypes.add(trigger.getTriggerType());
            }
        }
        snapshot.setTriggerTypeSummary(triggerTypes.isEmpty() ? null : String.join("/", triggerTypes));
    }

    private ScheduledTaskMonitorTrigger buildTrigger(Scheduled scheduled) {
        ScheduledTaskMonitorTrigger trigger = new ScheduledTaskMonitorTrigger();
        if (StringUtils.hasText(scheduled.cron())) {
            trigger.setTriggerType("cron");
            trigger.setCron(resolveStringValue(scheduled.cron()));
            trigger.setZone(resolveStringValue(scheduled.zone()));
        } else if (scheduled.fixedDelay() >= 0 || StringUtils.hasText(scheduled.fixedDelayString())) {
            trigger.setTriggerType("fixedDelay");
            trigger.setFixedDelay(scheduled.fixedDelay() >= 0 ? scheduled.fixedDelay() : null);
            trigger.setFixedDelayString(resolveStringValue(scheduled.fixedDelayString()));
        } else if (scheduled.fixedRate() >= 0 || StringUtils.hasText(scheduled.fixedRateString())) {
            trigger.setTriggerType("fixedRate");
            trigger.setFixedRate(scheduled.fixedRate() >= 0 ? scheduled.fixedRate() : null);
            trigger.setFixedRateString(resolveStringValue(scheduled.fixedRateString()));
        }
        trigger.setInitialDelay(scheduled.initialDelay() >= 0 ? scheduled.initialDelay() : null);
        trigger.setInitialDelayString(resolveStringValue(scheduled.initialDelayString()));
        trigger.setScheduler(resolveOptionalStringAttribute(scheduled, "scheduler"));
        trigger.setTimeUnit(resolveTimeUnit(scheduled).name());
        return trigger;
    }

    private void applyRuntimeState(ScheduledTaskMonitorSnapshot snapshot, ScheduledTaskRuntimeState state) {
        if (!Boolean.TRUE.equals(snapshot.getRegisteredInContext())) {
            snapshot.setRuntimeStatus("INACTIVE");
            snapshot.setRunning(false);
            return;
        }
        if (state == null || defaultZero(state.getRunCount()) <= 0) {
            snapshot.setRuntimeStatus("NEVER_RUN");
            snapshot.setRunning(false);
            return;
        }
        snapshot.setRunning(Boolean.TRUE.equals(state.getRunning()));
        snapshot.setRunCount(defaultZero(state.getRunCount()));
        snapshot.setSuccessCount(defaultZero(state.getSuccessCount()));
        snapshot.setFailureCount(defaultZero(state.getFailureCount()));
        snapshot.setLastStartedAt(state.getLastStartedAt());
        snapshot.setLastFinishedAt(state.getLastFinishedAt());
        snapshot.setLastDurationMs(state.getLastDurationMs());
        snapshot.setLastSuccess(state.getLastSuccess());
        snapshot.setLastErrorMessage(state.getLastErrorMessage());
        snapshot.setLastThreadName(state.getLastThreadName());
        if (Boolean.TRUE.equals(state.getRunning())) {
            snapshot.setRuntimeStatus("RUNNING");
        } else if (Boolean.TRUE.equals(state.getLastSuccess())) {
            snapshot.setRuntimeStatus("SUCCESS");
        } else {
            snapshot.setRuntimeStatus("FAILED");
        }
    }

    private Long resolveNextRunAt(ScheduledTaskMonitorSnapshot snapshot, ScheduledTaskRuntimeState state) {
        if (!Boolean.TRUE.equals(snapshot.getRegisteredInContext()) || snapshot.getTriggers().isEmpty()) {
            return null;
        }
        Long nextRunAt = null;
        for (ScheduledTaskMonitorTrigger trigger : snapshot.getTriggers()) {
            Long candidate = resolveNextRunAt(trigger, state);
            if (candidate == null) {
                continue;
            }
            nextRunAt = nextRunAt == null ? candidate : Math.min(nextRunAt, candidate);
        }
        return nextRunAt;
    }

    private Long resolveNextRunAt(ScheduledTaskMonitorTrigger trigger, ScheduledTaskRuntimeState state) {
        if (trigger == null || !StringUtils.hasText(trigger.getTriggerType())) {
            return null;
        }
        if ("cron".equals(trigger.getTriggerType())) {
            return resolveNextCronTime(trigger, state);
        }
        Long intervalMs = resolveIntervalMs(trigger);
        if (intervalMs == null) {
            return null;
        }
        Long initialDelayMs = resolveInitialDelayMs(trigger);
        if ("fixedRate".equals(trigger.getTriggerType())) {
            Long base = state != null && state.getLastStartedAt() != null
                ? state.getLastStartedAt()
                : applicationStartedAt + defaultZero(initialDelayMs);
            return base + intervalMs;
        }
        if ("fixedDelay".equals(trigger.getTriggerType())) {
            Long base = state != null && state.getLastFinishedAt() != null
                ? state.getLastFinishedAt()
                : applicationStartedAt + defaultZero(initialDelayMs);
            return base + intervalMs;
        }
        return null;
    }

    private Long resolveNextCronTime(ScheduledTaskMonitorTrigger trigger, ScheduledTaskRuntimeState state) {
        if (!StringUtils.hasText(trigger.getCron())) {
            return null;
        }
        try {
            TimeZone timeZone = StringUtils.hasText(trigger.getZone())
                ? TimeZone.getTimeZone(trigger.getZone())
                : TimeZone.getDefault();
            CronTrigger cronTrigger = new CronTrigger(trigger.getCron(), timeZone);
            Date lastActual = state != null && state.getLastStartedAt() != null
                ? new Date(state.getLastStartedAt())
                : new Date(applicationStartedAt);
            Date lastCompletion = state != null && state.getLastFinishedAt() != null
                ? new Date(state.getLastFinishedAt())
                : new Date(applicationStartedAt);
            SimpleTriggerContext triggerContext = new SimpleTriggerContext(lastActual, lastActual, lastCompletion);
            Date next = cronTrigger.nextExecutionTime(triggerContext);
            return next == null ? null : next.getTime();
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveIntervalMs(ScheduledTaskMonitorTrigger trigger) {
        TimeUnit timeUnit = resolveTimeUnit(trigger);
        if ("fixedRate".equals(trigger.getTriggerType())) {
            Long value = trigger.getFixedRate();
            if (value != null && value >= 0) {
                return timeUnit.toMillis(value);
            }
            return parseDurationMillis(trigger.getFixedRateString(), timeUnit);
        }
        if ("fixedDelay".equals(trigger.getTriggerType())) {
            Long value = trigger.getFixedDelay();
            if (value != null && value >= 0) {
                return timeUnit.toMillis(value);
            }
            return parseDurationMillis(trigger.getFixedDelayString(), timeUnit);
        }
        return null;
    }

    private Long resolveInitialDelayMs(ScheduledTaskMonitorTrigger trigger) {
        TimeUnit timeUnit = resolveTimeUnit(trigger);
        if (trigger.getInitialDelay() != null && trigger.getInitialDelay() >= 0) {
            return timeUnit.toMillis(trigger.getInitialDelay());
        }
        return parseDurationMillis(trigger.getInitialDelayString(), timeUnit);
    }

    private Long parseDurationMillis(String value, TimeUnit timeUnit) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return timeUnit.toMillis(Long.parseLong(value.trim()));
        } catch (Exception ignored) {
        }
        try {
            return Duration.parse(value.trim()).toMillis();
        } catch (Exception ignored) {
        }
        return null;
    }

    private TimeUnit resolveTimeUnit(ScheduledTaskMonitorTrigger trigger) {
        if (!StringUtils.hasText(trigger.getTimeUnit())) {
            return TimeUnit.MILLISECONDS;
        }
        try {
            return TimeUnit.valueOf(trigger.getTimeUnit());
        } catch (Exception ignored) {
            return TimeUnit.MILLISECONDS;
        }
    }

    private TimeUnit resolveTimeUnit(Scheduled scheduled) {
        try {
            Method method = Scheduled.class.getMethod("timeUnit");
            Object value = method.invoke(scheduled);
            if (value instanceof TimeUnit timeUnit) {
                return timeUnit;
            }
        } catch (Exception ignored) {
        }
        return TimeUnit.MILLISECONDS;
    }

    private String resolveOptionalStringAttribute(Scheduled scheduled, String attributeName) {
        try {
            Method method = Scheduled.class.getMethod(attributeName);
            Object value = method.invoke(scheduled);
            return value == null ? null : resolveStringValue(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveStringValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return applicationContext.getEnvironment().resolvePlaceholders(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String resolveModuleName(Class<?> candidateClass) {
        String className = candidateClass.getName();
        if (className.startsWith("cn.geelato.web.platform.")) {
            return "web-platform";
        }
        if (className.startsWith("cn.geelato.web.app.")) {
            return "web-app";
        }
        String prefix = "cn.geelato.";
        if (!className.startsWith(prefix)) {
            return null;
        }
        String remainder = className.substring(prefix.length());
        int idx = remainder.indexOf('.');
        return idx < 0 ? remainder : remainder.substring(0, idx);
    }

    private long defaultZero(Long value) {
        return value == null ? 0L : value;
    }
}
