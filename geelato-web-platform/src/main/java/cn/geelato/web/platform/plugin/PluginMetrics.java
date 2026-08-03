package cn.geelato.web.platform.plugin;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 插件调用指标（P2-D）。
 * <p>轻量进程内计数，记录每个插件的调用次数、成功/失败次数、累计耗时（毫秒）。
 * 供 {@code /pm/list} 展示；如需对接 Micrometer/Actuator，可在后续接入
 * {@code MeterRegistry} 时将本类的计数转发。</p>
 *
 * <p>采用 {@link AtomicLong} 而非 synchronized，读多写多场景下无锁高效。</p>
 *
 * @author geelato
 */
@Component
public class PluginMetrics {

    private final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<>();

    /**
     * 记录一次调用结果。
     *
     * @param pluginId    插件 id
     * @param success     是否成功
     * @param elapsedMs   耗时（毫秒）
     */
    public void record(String pluginId, boolean success, long elapsedMs) {
        if (pluginId == null || pluginId.isBlank()) {
            return;
        }
        Stat s = stats.computeIfAbsent(pluginId, k -> new Stat());
        s.total.incrementAndGet();
        s.elapsedMs.addAndGet(elapsedMs);
        if (success) {
            s.success.incrementAndGet();
        } else {
            s.failure.incrementAndGet();
        }
    }

    /** 获取指定插件统计快照（只读 Map）。 */
    public java.util.Map<String, Object> snapshot(String pluginId) {
        Stat s = stats.get(pluginId);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        long total = s == null ? 0 : s.total.get();
        long success = s == null ? 0 : s.success.get();
        long failure = s == null ? 0 : s.failure.get();
        long elapsed = s == null ? 0 : s.elapsedMs.get();
        m.put("total", total);
        m.put("success", success);
        m.put("failure", failure);
        m.put("elapsedMs", elapsed);
        m.put("avgMs", total == 0 ? 0 : (elapsed / total));
        return m;
    }

    private static class Stat {
        final AtomicLong total = new AtomicLong();
        final AtomicLong success = new AtomicLong();
        final AtomicLong failure = new AtomicLong();
        final AtomicLong elapsedMs = new AtomicLong();
    }
}
