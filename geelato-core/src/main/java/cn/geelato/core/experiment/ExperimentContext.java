package cn.geelato.core.experiment;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 请求级实验功能上下文（ThreadLocal）。
 *
 * <p>由 Web 层 {@code ExperimentFilter} 在请求入口解析 {@code X-Gl-Experiments}
 * header 后填充，请求结束 finally 清理。任意层（含 SQL 生成层静态代码）可经
 * {@link ExperimentGate} 判定。非 HTTP 线程（定时任务、ORM 事件线程池）无此上下文，
 * 判定自然回退为关（实验默认关）。
 *
 * <p>本类只承载"请求声明开启的功能名"，白名单与合并语义在 {@link ExperimentGate}。
 */
public final class ExperimentContext {

    private static final ThreadLocal<Set<String>> ENABLED = new ThreadLocal<>();

    private ExperimentContext() {
    }

    /** 声明本请求开启的功能名（累积；功能名统一小写）。 */
    public static void enable(Collection<String> features) {
        if (features == null || features.isEmpty()) {
            return;
        }
        Set<String> current = ENABLED.get();
        if (current == null) {
            current = new HashSet<>();
            ENABLED.set(current);
        }
        for (String feature : features) {
            if (feature != null && !feature.isBlank()) {
                current.add(feature.trim().toLowerCase());
            }
        }
    }

    /** 本请求是否声明开启了指定功能（不判白名单）。 */
    public static boolean isEnabled(String feature) {
        Set<String> current = ENABLED.get();
        return current != null && feature != null && current.contains(feature.toLowerCase());
    }

    /** 当前请求声明的功能名（只读视图）。 */
    public static Set<String> current() {
        Set<String> current = ENABLED.get();
        return current == null ? Collections.emptySet() : Collections.unmodifiableSet(current);
    }

    /** 请求结束清理（Filter finally 调用）。 */
    public static void clear() {
        ENABLED.remove();
    }
}
