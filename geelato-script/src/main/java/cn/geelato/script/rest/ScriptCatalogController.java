package cn.geelato.script.rest;

import cn.geelato.core.graal.GraalServiceDescription;
import cn.geelato.script.core.EngineVersionInfo;
import cn.geelato.script.core.ScriptEngine;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * kit 能力清单端点：速查面板 / 编辑器自动补全的数据源。
 */
@RestController
@RequestMapping("/api/script")
public class ScriptCatalogController {

    private final ScriptEngine scriptEngine;

    public ScriptCatalogController(ScriptEngine scriptEngine) {
        this.scriptEngine = scriptEngine;
    }

    @GetMapping("/catalog")
    public List<GraalServiceDescription> catalog(@RequestParam(required = false) String profile,
                                                 @RequestParam(required = false) String version) {
        return scriptEngine.kitCatalog(profile, version);
    }

    @GetMapping("/versions")
    public List<EngineVersionInfo> versions() {
        return scriptEngine.versions();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
