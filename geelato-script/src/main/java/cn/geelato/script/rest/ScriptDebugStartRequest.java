package cn.geelato.script.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 调试会话启动请求体（{@code POST /api/script/debug/start}，geelato.script.rest.enabled 门控）。
 * <p>
 * 入参对齐通用执行（content/lang/bindings/params/profileId/version + scriptName），额外带调试会话身份。
 * 不收任意 attributes，避免越权注入；{@code debugOwner} 供同源代理做会话归属校验。
 */
@Getter
@Setter
public class ScriptDebugStartRequest {

    /** js（缺省）；断点调试仅支持 javascript。 */
    private String lang;
    private String content;
    /** 脚本定位标识，用于调试 Source 名（断点持久）。 */
    private String scriptName;
    /** 引擎版本（空=缺省版本）。 */
    private String engineVersion;
    /** 场景 profile（空=default）。 */
    private String profileId;
    /** 业务输入（注入 $params）。 */
    private Map<String, Object> params;
    /** 注入变量（$ctx 等，受 profile 变量白名单过滤）。 */
    private Map<String, Object> bindings;
    /** 会话 id（空由引擎分配）。 */
    private String debugSessionId;
    /** 会话归属（供代理鉴权；空回落身份 userId）。 */
    private String debugOwner;
    /** inspector 端口（空/0 由引擎自动分配）。 */
    private Integer debugPort;
    /** 本会话超时毫秒（仅注入 $env，调试执行本身不受同步超时约束）。 */
    private Long timeoutMs;
}
