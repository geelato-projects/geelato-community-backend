package cn.geelato.alert.boot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geelato.alert.engine")
public class AlertRenderProperties {

    /** 渲染引擎开关（缺省关闭，关闭时 testRender 维持占位行为）。 */
    private boolean enabled = false;

    /** 单脚本超时毫秒（bea 设计：模板 ≤ 2s）。 */
    private long timeoutMs = 2_000;

    /** 工作台深链前缀（workbenchUrl 拼接）。 */
    private String workbenchBaseUrl = "/fms/exception-center";
}
