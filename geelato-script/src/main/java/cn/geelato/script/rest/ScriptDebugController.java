package cn.geelato.script.rest;

import cn.geelato.script.core.DebugSession;
import cn.geelato.script.core.DebugStatus;
import cn.geelato.script.core.ScriptDebugService;
import cn.geelato.script.core.ScriptExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 调试会话端点（{@code /api/script/debug/*}）：start/stop/status，驱动 GraalJS CDP inspector 长会话。
 * <p>
 * 默认不装配，{@code geelato.script.rest.enabled=true} 时随 {@link cn.geelato.script.boot.ScriptRestAutoConfiguration}
 * 开启，仅限内网可信环境。返回的 {@link DebugSession} 含 inspector {@code host}/{@code port}，
 * 面向浏览器的同源网关（如 geelato-web-designer）须剥离后再下发代理路径。
 */
@RestController
@RequestMapping("/api/script")
public class ScriptDebugController {

    private final ScriptDebugService debugService;

    public ScriptDebugController(ScriptDebugService debugService) {
        this.debugService = debugService;
    }

    @PostMapping("/debug/start")
    public DebugSession start(@RequestBody ScriptDebugStartRequest request) {
        return debugService.start(ScriptExecution.builder()
                .lang(request.getLang())
                .content(request.getContent())
                .scriptName(request.getScriptName())
                .engineVersion(request.getEngineVersion())
                .profileId(request.getProfileId())
                .params(request.getParams())
                .bindings(request.getBindings())
                .timeoutMs(request.getTimeoutMs())
                .debugMode(true)
                .debugSessionId(request.getDebugSessionId())
                .debugOwner(request.getDebugOwner())
                .debugPort(request.getDebugPort())
                .build());
    }

    @PostMapping("/debug/stop/{sessionId}")
    public Map<String, Object> stop(@PathVariable("sessionId") String sessionId) {
        debugService.stop(sessionId);
        return Map.of("sessionId", sessionId, "stopped", true);
    }

    @GetMapping("/debug/{sessionId}/status")
    public DebugStatus status(@PathVariable("sessionId") String sessionId) {
        return debugService.status(sessionId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
