package cn.geelato.ide.dto;

import lombok.Data;

import java.util.Map;

/**
 * 调试会话启动请求（浏览器 → studio BFF {@code POST /api/script/debug/start}）。
 * <p>
 * 字段对齐 dry-run（content + language + parameter + bindings + profile + version + scriptName），
 * BFF 负责补齐当前登录用户为会话 owner、转发到 worker {@code /script/debug/start}，
 * 并只回发同源代理信息（sessionId/wsProxyPath/devtoolsBase），绝不回发内网 inspector host:port。
 *
 * @author geelato
 */
@Data
public class IdeDebugRequest {
    /** 语言：断点调试仅 js。 */
    private String language;
    /** 脚本内容（js 源）。 */
    private String content;
    /** 脚本定位标识（调试 Source 名，断点持久）。 */
    private String scriptName;
    /** 试运行入参（注入 $params），与真机试运行一致。 */
    private Object parameter;
    /** 注入变量（$ctx 等）。 */
    private Map<String, Object> bindings;
    /** 场景能力档案 id（缺省 default）。 */
    private String profileId;
    /** 引擎版本（缺省 default-version）。 */
    private String engineVersion;
    /** 超时毫秒（仅注入 $env）。 */
    private Long timeoutMs;
}
