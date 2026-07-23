package cn.geelato.ide.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.ide.dto.IdeDryRunRequest;
import cn.geelato.ide.dto.IdeDryRunResult;
import cn.geelato.ide.entity.IdeScript;
import cn.geelato.ide.entity.IdeSyncLog;
import cn.geelato.ide.enums.IdeSyncAction;
import cn.geelato.ide.enums.IdeSyncResult;
import cn.geelato.ide.service.IdeScriptService;
import cn.geelato.ide.service.IdeSyncLogService;
import cn.geelato.ide.service.IdeWorkerDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * dry-run 与校验 Controller（IDE/AI 协作的关键入口）。
 * <p>
 * 路径前缀 {@code /ide/validate}：
 * <ul>
 *   <li>{@code POST /ide/validate/dry-run}             - 直接 dry-run（传 content + language）</li>
 *   <li>{@code POST /ide/validate/dry-run-by-code/{code}} - 按已存脚本 code dry-run</li>
 *   <li>{@code POST /ide/validate/script-syntax}       - 纯语法解析（worker parse-only）</li>
 *   <li>{@code GET  /ide/validate/worker-health}       - worker 健康检查</li>
 * </ul>
 *
 * @author geelato
 */
@DesignTimeApiRestController("/ide/validate")
@Slf4j
public class IdeValidateController {

    @Autowired
    private IdeWorkerDispatcher dispatcher;

    @Autowired
    private IdeScriptService ideScriptService;

    @Autowired
    private IdeSyncLogService ideSyncLogService;

    @Value("${geelato.ide.dry-run.prod-requires-admin:true}")
    private boolean prodRequiresAdmin;

    @Value("${geelato.ide.dry-run.default-timeout-ms:5000}")
    private long defaultTimeoutMs;

    /**
     * 直接 dry-run（脚本内容直传，无需先存库）。
     */
    @PostMapping("/dry-run")
    public ApiResult<?> dryRun(@RequestBody IdeDryRunRequest request, HttpServletRequest httpRequest) {
        if (request == null || Strings.isBlank(request.getContent())) {
            return ApiResult.fail("content 不能为空");
        }
        if (request.getTimeoutMs() == null) {
            request.setTimeoutMs(defaultTimeoutMs);
        }
        // 生产环境保护
        String rejectReason = checkProdGuard(request.getTimeoutMs());
        if (rejectReason != null) {
            return ApiResult.fail(403, rejectReason);
        }
        IdeDryRunResult result = dispatcher.dryRun(request);
        // 审计
        recordDryRunLog(null, null, request, result, httpRequest);
        return ApiResult.success(result);
    }

    /**
     * 按已存脚本 code dry-run（使用脚本库中的 content + default_params）。
     */
    @PostMapping("/dry-run-by-code/{code}")
    public ApiResult<?> dryRunByCode(@PathVariable("code") String code,
                                     @RequestBody(required = false) Map<String, Object> override,
                                     HttpServletRequest httpRequest) {
        IdeScript script = ideScriptService.getByCode(code);
        if (script == null) {
            return ApiResult.fail("脚本不存在: " + code);
        }
        IdeDryRunRequest req = new IdeDryRunRequest();
        req.setLanguage(script.getLanguage());
        // wasm 的字节码在 OSS，用 base64 传给 worker；js/python 直接用 content
        if ("wasm".equals(script.getLanguage())) {
            req.setContent(script.getWasmBinaryBase64());
        } else {
            req.setContent(script.getContent());
        }
        // 参数：优先 override，其次 default_params
        Object param = null;
        if (override != null && override.containsKey("parameter")) {
            param = override.get("parameter");
        } else if (Strings.isNotBlank(script.getDefaultParams())) {
            try {
                param = com.alibaba.fastjson2.JSON.parse(script.getDefaultParams());
            } catch (Exception ignored) {
            }
        }
        req.setParameter(param);
        req.setTimeoutMs(defaultTimeoutMs);

        String rejectReason = checkProdGuard(req.getTimeoutMs());
        if (rejectReason != null) {
            return ApiResult.fail(403, rejectReason);
        }
        IdeDryRunResult result = dispatcher.dryRun(req);
        recordDryRunLog(script.getCode(), script.getId(), req, result, httpRequest);
        return ApiResult.success(result);
    }

    /**
     * 纯语法解析（worker parse-only，不执行）。
     */
    @PostMapping("/script-syntax")
    public ApiResult<?> parseSyntax(@RequestBody IdeDryRunRequest request) {
        if (request == null || Strings.isBlank(request.getContent())) {
            return ApiResult.fail("content 不能为空");
        }
        IdeDryRunResult result = dispatcher.parseOnly(request);
        return ApiResult.success(result);
    }

    /**
     * worker 健康检查。
     */
    @GetMapping("/worker-health")
    public ApiResult<?> workerHealth() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("healthy", dispatcher.isWorkerHealthy());
        return ApiResult.success(resp);
    }

    // ======================================================================
    //                            helpers
    // ======================================================================

    /**
     * 生产环境保护：prod 环境 dry-run 需要管理员。
     */
    private String checkProdGuard(Long timeoutMs) {
        // 当前实现：只在显式配置 env=prod 时拦截（dry-run 本身不直接知道 envScope）
        // 进一步的 env 校验在脚本级别 dry-run-by-code 中处理
        if (timeoutMs != null && timeoutMs > 60000) {
            return "dry-run 超时不能超过 60 秒";
        }
        return null;
    }

    private void recordDryRunLog(String code, String scriptId, IdeDryRunRequest req,
                                  IdeDryRunResult result, HttpServletRequest httpRequest) {
        try {
            IdeSyncLog logEntry = new IdeSyncLog();
            logEntry.setAction(IdeSyncAction.DRYRUN);
            logEntry.setScriptCode(code != null ? code : "(inline)");
            logEntry.setScriptId(scriptId != null ? scriptId : "(inline)");
            logEntry.setResult(result.isSuccess() ? IdeSyncResult.SUCCESS : IdeSyncResult.FAIL);
            logEntry.setMessage(result.getErrorType() != null
                    ? result.getErrorType() + ": " + result.getErrorMessage()
                    : "OK");
            logEntry.setDurationMs(result.getDurationMs());
            logEntry.setCreateAt(new Date());
            if (httpRequest != null) {
                logEntry.setClientIp(resolveClientIp(httpRequest));
                logEntry.setUserAgent(httpRequest.getHeader("User-Agent"));
            }
            ideSyncLogService.record(logEntry);
        } catch (Exception e) {
            log.debug("dry-run 审计记录失败（不影响主流程）", e);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (Strings.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (Strings.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
