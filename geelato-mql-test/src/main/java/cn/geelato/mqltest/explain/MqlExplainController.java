package cn.geelato.mqltest.explain;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.mqltest.dto.MqlExecuteResult;
import cn.geelato.mqltest.dto.MqlExplainResult;
import cn.geelato.mqltest.dto.MqlScenario;
import cn.geelato.mqltest.dto.MqlScenarioResult;
import cn.geelato.mqltest.dto.MqlValidateResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * MQL explain / 调试 / 场景测试 工具链 Controller。
 * <p>
 * 仅在 {@code geelato.mql.playground.enabled=true} 时由 {@code MqlTestAutoConfiguration} 装配。
 * <p>
 * 支持"模拟身份"执行：请求体携带 loginName/tenantCode 时，在指定用户上下文中执行，
 * 使租户隔离 + 数据权限注入器照常工作，生成与生产一致的 SQL。
 */
@ApiRuntimeRestController("/mql")
@Slf4j
public class MqlExplainController {

    private final MqlExplainService explainService;
    private final MqlTestScenarioService scenarioService;
    private final MqlIdentitySupport identitySupport;

    @Autowired
    public MqlExplainController(MqlExplainService explainService,
                                @Autowired(required = false) MqlTestScenarioService scenarioService,
                                @Autowired(required = false) MqlIdentitySupport identitySupport) {
        this.explainService = explainService;
        this.scenarioService = scenarioService;
        this.identitySupport = identitySupport;
    }

    // ==================== 身份选择 ====================

    @GetMapping("/identities/tenants")
    public ApiResult<List<String>> tenants() {
        if (identitySupport == null) {
            return ApiResult.success(List.of());
        }
        return ApiResult.success(identitySupport.listTenantCodes());
    }

    @GetMapping("/identities/users")
    public ApiResult<List<Map<String, Object>>> users(@RequestParam(required = false) String tenantCode) {
        if (identitySupport == null) {
            return ApiResult.success(List.of());
        }
        return ApiResult.success(identitySupport.listIdentities(tenantCode));
    }

    // ==================== dry-run / 元数据 端点 ====================

    @PostMapping("/explain")
    public ApiResult<MqlExplainResult> explain(@RequestBody Map<String, Object> body) {
        String mql = stringValue(body, "mql");
        if (!StringUtils.hasText(mql)) {
            return ApiResult.fail("mql 参数不能为空");
        }
        try {
            MqlExplainResult result = runAs(body, () -> explainService.explain(mql));
            if (result.isSuccess()) {
                return ApiResult.success(result);
            }
            return ApiResult.fail(result, result.getError());
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @PostMapping("/validate")
    public ApiResult<MqlValidateResult> validate(@RequestBody Map<String, Object> body) {
        String mql = stringValue(body, "mql");
        if (!StringUtils.hasText(mql)) {
            return ApiResult.fail("mql 参数不能为空");
        }
        try {
            return ApiResult.success(runAs(body, () -> explainService.validate(mql)));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @GetMapping("/entities")
    public ApiResult<List<String>> entities() {
        return ApiResult.success(explainService.listEntities());
    }

    @GetMapping("/schema/{entity}")
    public ApiResult<Map<String, Object>> schema(@PathVariable("entity") String entity) {
        Map<String, Object> schema = explainService.getEntitySchema(entity);
        if (schema == null) {
            return ApiResult.fail("实体不存在: " + entity);
        }
        return ApiResult.success(schema);
    }

    // ==================== 真实执行端点 ====================

    @PostMapping("/execute")
    public ApiResult<MqlExecuteResult> execute(@RequestBody Map<String, Object> body) {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源（JdbcTemplate 不可用），无法执行。请在宿主应用配置 spring.datasource.primary.* 后重启。");
        }
        String mql = stringValue(body, "mql");
        if (!StringUtils.hasText(mql)) {
            return ApiResult.fail("mql 参数不能为空");
        }
        try {
            return ApiResult.success(runAs(body, () -> scenarioService.executeMql(mql)));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    // ==================== 场景集端点 ====================

    @GetMapping("/scenarios")
    public ApiResult<Map<String, List<MqlScenario>>> scenarios() {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源");
        }
        return ApiResult.success(scenarioService.listByCategory());
    }

    @PostMapping("/scenarios/run/{id}")
    public ApiResult<MqlScenarioResult> runScenario(@PathVariable("id") String id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源");
        }
        MqlScenario target = scenarioService.listScenarios().stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return ApiResult.fail("场景不存在: " + id);
        }
        try {
            return ApiResult.success(runAs(body, () -> scenarioService.executeScenario(target)));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @PostMapping("/scenarios/runAll")
    public ApiResult<Map<String, Object>> runAllScenarios(@RequestBody(required = false) Map<String, Object> body) {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源");
        }
        try {
            return ApiResult.success(runAs(body, () -> scenarioService.runAllScenarios()));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @PostMapping("/scenarios/initSchema")
    public ApiResult<String> initSchema() {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源");
        }
        return ApiResult.success(scenarioService.initSchema());
    }

    @PostMapping("/scenarios/cleanup")
    public ApiResult<String> cleanup() {
        if (scenarioService == null) {
            return ApiResult.fail("未配置数据源");
        }
        return ApiResult.success(scenarioService.cleanupAllData());
    }

    // ==================== 辅助 ====================

    private <T> T runAs(Map<String, Object> body, java.util.concurrent.Callable<T> action) throws Exception {
        if (identitySupport == null) {
            return action.call();
        }
        return identitySupport.runAs(stringValue(body, "loginName"), stringValue(body, "tenantCode"), action);
    }

    private static String stringValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
