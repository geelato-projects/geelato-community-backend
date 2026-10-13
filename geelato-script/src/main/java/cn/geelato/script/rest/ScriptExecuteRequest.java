package cn.geelato.script.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 通用执行端点请求体（测试/调试用途，geelato.script.rest.enabled 门控）。
 * 不收 attributes/identity，避免任意属性与身份伪造。
 */
@Getter
@Setter
public class ScriptExecuteRequest {

    /** js（缺省）/ python。 */
    private String lang;
    private String content;
    /** 脚本定位标识（规则 code 等），仅用于执行日志。 */
    private String scriptName;
    /** 引擎版本（空=缺省版本）。 */
    private String engineVersion;
    /** 场景 profile（空=default）。 */
    private String profileId;
    /** 业务输入（注入 $params）。 */
    private Map<String, Object> params;
    /** 注入变量（$ctx 等，受 profile 变量白名单过滤）。 */
    private Map<String, Object> bindings;
    private Long timeoutMs;
}
