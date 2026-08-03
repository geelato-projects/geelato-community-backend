package cn.geelato.web.platform.plugin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.plugin")
public class PluginConfigurationProperties {
    /**
     * 插件 jar 所在目录（可为共享卷挂载点，使多节点共享同一份插件）。
     */
    private String pluginDirectory = "plugins";
    /**
     * pf4j-update 仓库目录。
     */
    private String pluginRepository = "plugins/repository";
    /**
     * 插件开关配置目录（共享卷挂载点）。
     * <p>存放平台级 {@code plugins-enabled.json} 与租户级 {@code tenants/tenant_{code}.json}。
     * 多节点部署时所有节点应指向同一共享路径，保证开关状态一致。</p>
     */
    private String configDirectory = "plugins-config";
    /**
     * 平台级/租户级开关本地缓存时长（秒）。变更最长在此时长内对全部节点生效。
     */
    private long cacheTtlSeconds = 5L;
    /**
     * 插件扩展方法调用超时（毫秒），0 表示不限制。
     * <p>防止插件卡死（如 native 推理死循环）拖垮调用线程。</p>
     */
    private long invocationTimeoutMillis = 60000L;
    /**
     * 是否校验插件 jar 签名（P2-C，默认关闭，避免阻塞现有无签名插件）。
     */
    private boolean signatureVerify = false;
}
