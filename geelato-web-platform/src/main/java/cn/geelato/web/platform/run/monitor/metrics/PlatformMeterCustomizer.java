package cn.geelato.web.platform.run.monitor.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 平台指标治理器。
 * <p>
 * 两项职责：
 * <ol>
 *   <li><b>公共标签</b>：为所有指标序列注入 {@code application}（取 {@code spring.application.name}）
 *       与 {@code module} 标签，便于在多实例/多模块的 Prometheus 中区分来源。</li>
 *   <li><b>高基数防护</b>（业界标准做法）：
 *     <ul>
 *       <li>丢弃 {@code http.server.requests} 中 {@code uri} 为 {@code /UNKNOWN} 或以 {@code /**}
 *           结尾的未识别模板序列，防止基数爆炸。</li>
 *       <li>对 {@code http.server.requests} 的 {@code uri} 标签设上限（{@link #MAX_HTTP_URI_TAGS}），
 *           超出后新标签折叠为 {@code none}，兜底防 API 路径失控。</li>
 *       <li>把 {@code exception} 全限定类名替换为简单类名，降低标签取值数。</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * 参考 geelato-message 的 {@code MessageMeterCustomizer} 同款思路。
 *
 * @author geelato
 */
public class PlatformMeterCustomizer implements MeterRegistryCustomizer<MeterRegistry> {

    /**
     * {@code http.server.requests} 指标允许的 {@code uri} 标签取值上限。
     * <p>
     * 超过后新出现的 uri 会被折叠为 {@code none}，避免因路由模板未归一化导致序列数失控。
     * 平台常规 API 路由数量远小于此值，设 200 留足余量。
     */
    static final int MAX_HTTP_URI_TAGS = 200;

    static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    private final PlatformMetricsProperties properties;
    private final Environment environment;

    public PlatformMeterCustomizer(PlatformMetricsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void customize(MeterRegistry registry) {
        // 1. 公共标签
        String application = resolveApplication();
        String module = resolveModule();
        registry.config().commonTags("application", application, "module", module);

        // 2. 高基数防护
        registry.config().meterFilter(denyUnknownHttpUris());
        registry.config().meterFilter(capHttpUriTags());
        registry.config().meterFilter(simplifyExceptionTag());
    }

    private String resolveApplication() {
        PlatformMetricsProperties.CommonTags tags = properties.getCommonTags();
        if (tags != null && StringUtils.hasText(tags.getApplication())) {
            return tags.getApplication().trim();
        }
        String fromEnv = environment == null ? null : environment.getProperty("spring.application.name");
        return StringUtils.hasText(fromEnv) ? fromEnv.trim() : "unknown";
    }

    private String resolveModule() {
        PlatformMetricsProperties.CommonTags tags = properties.getCommonTags();
        if (tags != null && StringUtils.hasText(tags.getModule())) {
            return tags.getModule().trim();
        }
        return "geelato-web-platform";
    }

    /**
     * 丢弃 {@code http.server.requests} 中 uri 为 /UNKNOWN 或以 /** 结尾的序列。
     * <p>
     * 这些值通常是 micrometer 未能识别路由模板时产生的兜底标签，基数不可控。
     */
    static MeterFilter denyUnknownHttpUris() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (!HTTP_SERVER_REQUESTS.equals(id.getName())) {
                    return MeterFilterReply.NEUTRAL;
                }
                String value = id.getTag("uri");
                if ("/UNKNOWN".equals(value) || value == null || value.endsWith("/**")) {
                    return MeterFilterReply.DENY;
                }
                return MeterFilterReply.NEUTRAL;
            }
        };
    }

    /**
     * 对 {@code http.server.requests} 的 {@code uri} 标签设上限。
     * <p>
     * 当不同 uri 取值数超过 {@link #MAX_HTTP_URI_TAGS} 后，新的 {@code http.server.requests}
     * 序列整体被拒绝（兜底防护，避免路由模板未归一化导致序列数失控）。
     * 平台常规 API 路由数量远小于此值，正常情况下不会触发。
     */
    static MeterFilter capHttpUriTags() {
        // 达到上限后拒绝该 meter 的新序列。maximumAllowableTags 会对指定 (meterName, tagKey)
        // 维度计数，超限时应用第四参 filter——这里用 deny() 拒绝。
        return MeterFilter.maximumAllowableTags(HTTP_SERVER_REQUESTS, "uri", MAX_HTTP_URI_TAGS, MeterFilter.deny());
    }

    /**
     * 把 {@code exception} 标签的全限定类名替换为简单类名，降低取值数。
     * <p>
     * 例如 {@code java.lang.NullPointerException} → {@code NullPointerException}。
     * 全局作用于所有指标的 {@code exception} 标签（仅当该标签存在时才替换，不影响无此标签的指标）。
     */
    static MeterFilter simplifyExceptionTag() {
        return MeterFilter.replaceTagValues("exception", value -> {
            if (!StringUtils.hasText(value)) {
                return value;
            }
            int idx = value.lastIndexOf('.');
            return idx >= 0 ? value.substring(idx + 1) : value;
        });
    }
}
