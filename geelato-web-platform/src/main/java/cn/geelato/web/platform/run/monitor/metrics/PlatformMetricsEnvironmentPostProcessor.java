package cn.geelato.web.platform.run.monitor.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台监控零配置默认值注入器。
 * <p>
 * 设计目标：{@code geelato-web-platform} 引入 actuator + micrometer 依赖后，
 * 应用 {@code /actuator/prometheus}、{@code /actuator/health/live|ready} 等端点立即可用，
 * 用户无需任何额外配置。
 * <p>
 * 实现机制：作为 {@link EnvironmentPostProcessor} 在 {@code ApplicationEnvironmentPreparedEvent}
 * 阶段执行，注入一个低优先级的 {@link MapPropertySource}（放在最后，用户配置/系统环境变量
 * 均可覆盖）。每个属性项仅在 {@code environment} 中不存在时才写入，绝不覆盖用户已显式设置的值。
 * <p>
 * 与 {@code geelato-orm} 的 {@code AtomikosEnvironmentPostProcessor} 同款机制。
 * <p>
 * 默认值遵循 SRE 黄金信号标准：
 * <ul>
 *   <li>RED：{@code http.server.requests} 开启直方图 + SLO bucket，支持 p95/p99 与 SLO 计算</li>
 *   <li>USE：HikariCP 连接池、JVM 指标由 actuator 自带</li>
 *   <li>K8s 健康语义：liveness/readiness 分组分离，degraded 映射为 HTTP 200</li>
 * </ul>
 *
 * @author geelato
 */
public class PlatformMetricsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 注入的 PropertySource 名称（用户可在调试时据此定位来源）。
     */
    private static final String PROPERTY_SOURCE_NAME = "geelatoPlatformMetricsDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // ===== actuator 端点暴露 =====
        // health 子路径 /actuator/health/live|ready 对 K8s 探针友好；info/metrics 便于排查，生产可收窄为 health,prometheus
        putIfAbsent(defaults, environment, "management.endpoints.web.exposure.include",
                "health,info,prometheus,metrics");

        // ===== 健康检查 / K8s 探针 =====
        putIfAbsent(defaults, environment, "management.endpoint.health.probes.enabled", "true");
        putIfAbsent(defaults, environment, "management.endpoint.health.show-details", "always");
        // liveness = 进程是否存活（不重启我）；readiness = 业务是否就绪（可接流量）
        putIfAbsent(defaults, environment, "management.endpoint.health.group.liveness.include",
                "livenessState,platformHealth");
        putIfAbsent(defaults, environment, "management.endpoint.health.group.readiness.include",
                "readinessState,platformReadiness");
        // 状态优先级：down 最严重，degraded 居中（部分可用），up 最后
        putIfAbsent(defaults, environment, "management.endpoint.health.status.order",
                "down,out-of-service,degraded,unknown,up");
        // degraded 返回 200（而非 503），避免 K8s readiness 误判重启；告警由指标侧而非 HTTP 码触发
        putIfAbsent(defaults, environment, "management.endpoint.health.status.http-mapping.degraded", "200");

        // ===== Prometheus 指标导出 =====
        putIfAbsent(defaults, environment, "management.prometheus.metrics.export.enabled", "true");

        // ===== HTTP 服务请求指标（RED：Rate/Error/Duration） =====
        // 开启自动计时 + 直方图，使 histogram_quantile(p95/p99) 可算
        putIfAbsent(defaults, environment, "management.metrics.web.server.request.autotime.enabled", "true");
        putIfAbsent(defaults, environment, "management.metrics.web.server.request.autotime.percentiles-histogram", "true");
        // 黄金 SLO bucket：覆盖快接口(50ms)到慢接口(5s)，适配 SLO/错误预算计算
        putIfAbsent(defaults, environment, "management.metrics.distribution.slo.http.server.requests",
                "50ms,100ms,250ms,500ms,1s,5s");

        // ===== gzip 压缩（/actuator/prometheus 输出可达数十 KB，压缩降 5 倍流量） =====
        // mime-types 仅在用户未配时给默认；若用户已配，追加 openmetrics MIME（保证不丢已有项）
        contributeCompression(defaults, environment);

        if (defaults.isEmpty()) {
            return;
        }
        MutablePropertySources sources = environment.getPropertySources();
        // 放在最后：优先级最低，用户配置/系统属性/环境变量均可覆盖
        if (!sources.contains(PROPERTY_SOURCE_NAME)) {
            sources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    @Override
    public int getOrder() {
        // 低于 geelato-orm 的 HIGHEST_PRECEDENCE，避免与数据源后处理器抢跑
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 仅当 {@code environment} 中不存在该 key 时，写入默认值。
     */
    private void putIfAbsent(Map<String, Object> defaults, ConfigurableEnvironment environment,
                             String key, String value) {
        if (!environment.containsProperty(key)) {
            defaults.put(key, value);
        }
    }

    /**
     * gzip 压缩配置：
     * <ul>
     *   <li>{@code server.compression.enabled}：缺失则设 true</li>
     *   <li>{@code server.compression.min-response-size}：缺失则设 1024</li>
     *   <li>{@code server.compression.mime-types}：缺失则设默认（含 openmetrics）；用户已配则保持不动</li>
     * </ul>
     * <p>
     * 设计取舍：若用户已配置 mime-types，不擅自追加 {@code application/openmetrics-text}。
     * 原因：EnvironmentPostProcessor 注入的是低优先级 PropertySource，无法覆盖用户已设的高优先级值；
     * 强行改写用户的 PropertySource 会破坏可预期性。因此用户若自定义了 mime 列表，应自行包含
     * {@code application/openmetrics-text} 以确保 /actuator/prometheus 被 gzip 压缩。
     * （即使未压缩，Prometheus 端点仍正常工作，仅抓取流量偏大。）
     */
    private void contributeCompression(Map<String, Object> defaults, ConfigurableEnvironment environment) {
        putIfAbsent(defaults, environment, "server.compression.enabled", "true");
        putIfAbsent(defaults, environment, "server.compression.min-response-size", "1024");
        // 仅在用户未配 mime-types 时给默认（含 openmetrics）；已配则保持不动
        putIfAbsent(defaults, environment, "server.compression.mime-types",
                "application/openmetrics-text,text/html,text/xml,text/plain,text/css,text/javascript,"
                        + "application/javascript,application/json");
    }
}
