package cn.geelato.alert.boot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警引擎配置（投递/调度/接收人兜底）。渲染预算（timeoutMs/workbenchBaseUrl）与总开关 enabled
 * 在 {@link AlertRenderProperties}（同前缀 geelato.alert.engine，字段不重叠）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "geelato.alert.engine")
public class AlertEngineProperties {

    /** geelato-message 服务基址（如 http://127.0.0.1:8086）；引擎启用时必填。 */
    private String messageBaseUrl;

    /** message HTTP 调用超时毫秒（连接/读/写同值）。 */
    private long messageTimeoutMs = 10_000;

    /** 集成系统编码（message 开启入队鉴权时随 X-System-Code 头传递；空=匿名）。 */
    private String messageSystemCode;

    /** 集成系统 api key（随 Authorization: Bearer 头传递；空=匿名）。 */
    private String messageApiKey;

    /** 接收人求解为空时的兜底角色 code；空=不兜底，记录 FAIL 硬失败。 */
    private String fallbackRoleCode;

    /** 引擎主循环间隔秒（冲刷/投递/重发三段串行扫描）。 */
    private int tickSeconds = 5;

    /** 单轮投递最大处理记录数。 */
    private int deliverBatchSize = 20;

    /** 投递失败最大重试次数（超过转 FAIL 死信等手动重发）。 */
    private int maxRetry = 3;

    /** 投递重试退避基数秒（第 n 次重试等待 backoffBaseSeconds * 2^n）。 */
    private long backoffBaseSeconds = 60;

    /** SCHEDULE 规则 cron 轮询间隔秒。 */
    private int scheduleTickSeconds = 30;

    /** 事件源扫描回看秒数（引擎启动水位线 = now - 回看；防重启窗口内漏扫）。 */
    private int sourceLookbackSeconds = 600;

    /** 事件源新增扫描单轮最大行数。 */
    private int sourceBatchSize = 200;
}
