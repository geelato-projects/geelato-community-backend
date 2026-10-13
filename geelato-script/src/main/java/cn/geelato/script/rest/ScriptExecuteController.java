package cn.geelato.script.rest;

import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptIssue;
import cn.geelato.script.core.ScriptResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通用执行端点（测试/调试辅助，非模块间集成通道）：经 SDK 门面执行/预检，engineVersion 指定版本路由。
 * 默认不装配，geelato.script.rest.enabled=true 时随 ScriptRestAutoConfiguration 开启，仅限内网可信环境。
 */
@RestController
@RequestMapping("/api/script")
public class ScriptExecuteController {

    private final ScriptEngine scriptEngine;

    public ScriptExecuteController(ScriptEngine scriptEngine) {
        this.scriptEngine = scriptEngine;
    }

    @PostMapping("/execute")
    public ScriptResult execute(@RequestBody ScriptExecuteRequest request) {
        return scriptEngine.execute(ScriptExecution.builder()
                .lang(request.getLang())
                .content(request.getContent())
                .scriptName(request.getScriptName())
                .engineVersion(request.getEngineVersion())
                .profileId(request.getProfileId())
                .params(request.getParams())
                .bindings(request.getBindings())
                .timeoutMs(request.getTimeoutMs())
                .build());
    }

    @PostMapping("/parse")
    public List<ScriptIssue> parse(@RequestBody ScriptParseRequest request) {
        return scriptEngine.parseOnly(request.getLang(), request.getContent(), request.getEngineVersion());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
