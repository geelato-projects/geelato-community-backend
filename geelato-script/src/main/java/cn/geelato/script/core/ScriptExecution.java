package cn.geelato.script.core;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 单脚本执行请求（告警模板 title/content 各一段等场景）。
 */
@Getter
@Builder
public class ScriptExecution {

    /** js（缺省）/ python。 */
    private final String lang;
    private final String content;
    /** 脚本定位标识（规则 code/模板 code 等），仅用于执行日志，不注入脚本。 */
    private final String scriptName;
    /** 引擎版本（缺省取 default-version）。 */
    private final String engineVersion;
    /** 场景能力档案 id（缺省 default）。 */
    private final String profileId;
    /** 注入变量：key 为 $ctx/$params 等带 $ 前缀名，value 须为 Map/List/String/Number/Boolean。 */
    private final Map<String, Object> bindings;
    /** 业务输入便捷字段：引擎归一注入为 $params（显式 bindings 含 $params 时不覆盖）。 */
    private final Map<String, Object> params;
    /** 执行级属性袋（kit 经 KitContext 读取，不注入脚本）。 */
    private final Map<String, Object> attributes;
    /** 本执行超时毫秒（缺省用 profile.defaultTimeoutMs）。 */
    private final Long timeoutMs;
    /** 显式身份（异步消费方传入；缺省在调用线程捕获）。 */
    private final cn.geelato.script.core.IdentitySnapshot identity;
    /** 写通道 dry-run 覆盖（null 用引擎配置 dao.dry-run 缺省 true）。 */
    private final Boolean dryRun;
    /** 调试模式：经 CDP inspector 挂起执行（start/stop 会话，由 ScriptDebugService 承载，非同步 execute）。 */
    private final boolean debugMode;
    /** 调试会话 id（debugMode 时由调用方指定或由引擎分配）。 */
    private final String debugSessionId;
    /** 调试会话归属标识（平台用户/会话，供同源代理鉴权；空则回落身份 userId）。 */
    private final String debugOwner;
    /** 调试 inspector 端口（null=0 由引擎自动分配空闲端口）。 */
    private final Integer debugPort;
}
