package cn.geelato.script.rest;

import cn.geelato.script.core.EngineAdmin;
import cn.geelato.script.core.EngineVersionInfo;
import cn.geelato.script.core.ScriptEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 引擎版本管理端点（测试/运维调试辅助）：热切缺省版本、弃用、下线。
 * 默认不装配，geelato.script.admin.enabled=true 时开启，与 rest.enabled 相互独立。
 */
@RestController
@RequestMapping("/api/script/admin")
public class ScriptAdminController {

    private final ScriptEngine scriptEngine;
    private final EngineAdmin engineAdmin;

    public ScriptAdminController(ScriptEngine scriptEngine, EngineAdmin engineAdmin) {
        this.scriptEngine = scriptEngine;
        this.engineAdmin = engineAdmin;
    }

    @GetMapping("/versions")
    public List<EngineVersionInfo> versions() {
        return scriptEngine.versions();
    }

    @PostMapping("/version/default")
    public EngineVersionInfo setDefault(@RequestBody VersionRequest request) {
        return engineAdmin.setDefault(request.getVersionId());
    }

    @PostMapping("/version/deprecate")
    public EngineVersionInfo deprecate(@RequestBody VersionRequest request) {
        return engineAdmin.deprecate(request.getVersionId(), request.getMigrateHint());
    }

    @PostMapping("/version/retire")
    public EngineVersionInfo retire(@RequestBody VersionRequest request) {
        return engineAdmin.retire(request.getVersionId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @lombok.Getter
    @lombok.Setter
    public static class VersionRequest {
        private String versionId;
        private String migrateHint;
    }
}
