package cn.geelato.web.platform.run.monitor.methodstats;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * Controller 方法调用统计数据接口（自动加 /api 前缀 → /api/method-stats/**）。
 * 供 method-stats.html 消费：全量条目（含 0 值）+ 汇总 + 清零。
 */
@ApiRuntimeRestController("/method-stats")
public class ControllerMethodStatsApiController {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    private final ControllerMethodCallStats stats;

    public ControllerMethodStatsApiController(ControllerMethodCallStats stats) {
        this.stats = stats;
    }

    /** GET /api/method-stats — 全部 API 方法调用计数（count 降序，含未调用的 0 值条目）。 */
    @GetMapping(produces = JSON_UTF8)
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.success(stats.snapshot());
    }

    /** POST /api/method-stats/reset — 全部计数归 0（保留条目）。 */
    @PostMapping(value = "/reset", produces = JSON_UTF8)
    public ApiResult<Void> reset() {
        stats.resetAll();
        return ApiResult.successNoResult();
    }
}
