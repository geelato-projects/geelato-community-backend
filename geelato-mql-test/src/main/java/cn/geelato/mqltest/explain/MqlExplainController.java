package cn.geelato.mqltest.explain;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.mqltest.dto.MqlExplainResult;
import cn.geelato.mqltest.dto.MqlValidateResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * MQL explain / 调试工具链 Controller。
 * <p>
 * 提供 MQL → SQL 的 dry-run 解析、校验、实体元数据自省。
 * 仅在 {@code geelato.mql.playground.enabled=true} 时由 {@code MqlTestAutoConfiguration} 装配。
 */
@ApiRuntimeRestController("/mql")
@Slf4j
public class MqlExplainController {

    private final MqlExplainService explainService;

    public MqlExplainController(MqlExplainService explainService) {
        this.explainService = explainService;
    }

    /**
     * dry-run：解析 MQL JSON 并返回生成的 SQL/params/types，不执行。
     *
     * @param body 请求体，{"mql": "MQL JSON 文本"}
     */
    @PostMapping("/explain")
    public ApiResult<MqlExplainResult> explain(@RequestBody Map<String, String> body) {
        String mql = body.get("mql");
        if (mql == null || mql.isBlank()) {
            return ApiResult.fail("mql 参数不能为空");
        }
        MqlExplainResult result = explainService.explain(mql);
        if (result.isSuccess()) {
            return ApiResult.success(result);
        }
        return ApiResult.fail(result, result.getError());
    }

    /**
     * 仅校验 MQL JSON 合法性。
     */
    @PostMapping("/validate")
    public ApiResult<MqlValidateResult> validate(@RequestBody Map<String, String> body) {
        String mql = body.get("mql");
        if (mql == null || mql.isBlank()) {
            return ApiResult.fail("mql 参数不能为空");
        }
        return ApiResult.success(explainService.validate(mql));
    }

    /**
     * 获取可用实体名列表。
     */
    @GetMapping("/entities")
    public ApiResult<List<String>> entities() {
        return ApiResult.success(explainService.listEntities());
    }

    /**
     * 获取实体元数据（字段/列名/类型/JSON/外键）。
     */
    @GetMapping("/schema/{entity}")
    public ApiResult<Map<String, Object>> schema(@PathVariable("entity") String entity) {
        Map<String, Object> schema = explainService.getEntitySchema(entity);
        if (schema == null) {
            return ApiResult.fail("实体不存在: " + entity);
        }
        return ApiResult.success(schema);
    }
}
