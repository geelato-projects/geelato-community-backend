package cn.geelato.app.scaffold.boot;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@ApiRuntimeRestController("/scaffold")
public class AppScaffoldReadyController {

    private final JdbcTemplate primaryJdbcTemplate;
    private final AppScaffoldProperties appScaffoldProperties;
    private final Environment environment;

    public AppScaffoldReadyController(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                                      AppScaffoldProperties appScaffoldProperties,
                                      Environment environment) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.appScaffoldProperties = appScaffoldProperties;
        this.environment = environment;
    }

    @GetMapping("/ready")
    public ApiResult<Map<String, Object>> ready() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app", environment.getProperty("spring.application.name", "unknown-app"));
        payload.put("starter", "geelato-app-scaffold-starter");
        payload.put("capabilities", AppScaffoldCapability.builtinCapabilityIds());
        payload.put("database", primaryJdbcTemplate.queryForObject("select 'ready'", String.class));
        return ApiResult.success(payload, "app scaffold starter is ready");
    }
}
