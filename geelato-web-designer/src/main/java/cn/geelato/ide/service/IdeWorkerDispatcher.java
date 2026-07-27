package cn.geelato.ide.service;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.ide.dto.IdeDryRunRequest;
import cn.geelato.ide.dto.IdeDryRunResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * web-platform → script-worker HTTP 调度器。
 * <p>
 * 职责：
 * <ol>
 *   <li>构造请求（透传当前用户/租户上下文，通过 HTTP header）</li>
 *   <li>调用 worker（带超时控制）</li>
 *   <li>失败降级：worker 不可达时返回明确错误，不挂主进程</li>
 * </ol>
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeWorkerDispatcher {

    @Value("${geelato.ide.worker.baseUrl:http://localhost:8087}")
    private String workerBaseUrl;

    @Value("${geelato.ide.worker.connectTimeoutMs:3000}")
    private int connectTimeoutMs;

    @Value("${geelato.ide.worker.readTimeoutMs:30000}")
    private int readTimeoutMs;

    private static final MediaType JSON_MT = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient client;

    private synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false)
                    .build();
        }
        return client;
    }

    /**
     * 执行 dry-run（worker 强制事务回滚，永不落库）。
     */
    public IdeDryRunResult dryRun(IdeDryRunRequest request) {
        long start = System.currentTimeMillis();
        IdeDryRunResult result = new IdeDryRunResult();
        result.setSource("worker");

        // 构造 worker 请求
        JSONObject body = new JSONObject();
        body.put("language", (request.getLanguage() == null || request.getLanguage().isBlank()) ? "js" : request.getLanguage());
        body.put("content", request.getContent());
        body.put("parameter", request.getParameter());
        body.put("timeoutMs", request.getTimeoutMs() != null ? request.getTimeoutMs() : 5000L);
        body.put("captureOutput", request.getCaptureOutput() != null ? request.getCaptureOutput() : Boolean.TRUE);
        body.put("debugMode", Boolean.TRUE.equals(request.getDebugMode()));

        Request.Builder rb = new Request.Builder()
                .url(workerBaseUrl + "/script/execute")
                .post(RequestBody.create(body.toJSONString(), JSON_MT));

        // 透传 SecurityContext → header
        User user = SecurityContext.getCurrentUser();
        if (user != null) {
            rb.addHeader("X-Geelato-User-Id", (user.getUserId() == null || user.getUserId().isBlank()) ? "system" : user.getUserId());
            rb.addHeader("X-Geelato-User-Name", (user.getUserName() == null || user.getUserName().isBlank()) ? "system" : user.getUserName());
            if (user.getBuId() != null) {
                rb.addHeader("X-Geelato-Bu-Id", user.getBuId());
            }
            if (user.getDefaultOrgId() != null) {
                rb.addHeader("X-Geelato-Org-Id", user.getDefaultOrgId());
            }
        }
        Tenant tenant = SecurityContext.getCurrentTenant();
        if (tenant != null && Strings.isNotBlank(tenant.getCode())) {
            rb.addHeader("X-Geelato-Tenant-Code", tenant.getCode());
        }

        try (Response resp = client().newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                result.setSuccess(false);
                result.setErrorType("worker_http_" + resp.code());
                result.setErrorMessage("worker HTTP " + resp.code() + ": " + truncate(respBody, 500));
                result.setDurationMs(System.currentTimeMillis() - start);
                return result;
            }
            JSONObject json = JSON.parseObject(respBody);
            result.setSuccess(json.getBooleanValue("success"));
            result.setReturnValue(json.get("returnValue"));
            result.setLogs(json.getJSONArray("logs") != null
                    ? json.getJSONArray("logs").toJavaList(String.class)
                    : new java.util.ArrayList<>());
            result.setErrorType(json.getString("errorType"));
            result.setErrorMessage(json.getString("errorMessage"));
            result.setErrorLocation(json.getString("errorLocation"));
            result.setStackTrace(json.getJSONArray("stackTrace") != null
                    ? json.getJSONArray("stackTrace").toJavaList(String.class)
                    : new java.util.ArrayList<>());
            result.setDurationMs(json.getLongValue("durationMs"));
            result.setRolledBack(json.getBooleanValue("rolledBack"));
            return result;
        } catch (Exception e) {
            log.error("调用 worker 失败: baseUrl={}", workerBaseUrl, e);
            result.setSuccess(false);
            result.setErrorType("worker_unavailable");
            result.setErrorMessage("worker 不可达: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            result.setSource("unavailable");
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    /**
     * 纯语法解析（worker parse-only）。
     */
    public IdeDryRunResult parseOnly(IdeDryRunRequest request) {
        long start = System.currentTimeMillis();
        IdeDryRunResult result = new IdeDryRunResult();
        result.setSource("worker");

        JSONObject body = new JSONObject();
        body.put("language", (request.getLanguage() == null || request.getLanguage().isBlank()) ? "js" : request.getLanguage());
        body.put("content", request.getContent());

        Request.Builder rb = new Request.Builder()
                .url(workerBaseUrl + "/script/parse")
                .post(RequestBody.create(body.toJSONString(), JSON_MT));

        try (Response resp = client().newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                result.setSuccess(false);
                result.setErrorType("worker_http_" + resp.code());
                result.setErrorMessage("worker HTTP " + resp.code());
                result.setDurationMs(System.currentTimeMillis() - start);
                return result;
            }
            JSONObject json = JSON.parseObject(respBody);
            result.setSuccess(json.getBooleanValue("success"));
            result.setErrorType(json.getString("errorType"));
            result.setErrorMessage(json.getString("errorMessage"));
            result.setDurationMs(json.getLongValue("durationMs"));
            return result;
        } catch (Exception e) {
            log.error("调用 worker parse 失败", e);
            result.setSuccess(false);
            result.setErrorType("worker_unavailable");
            result.setErrorMessage("worker 不可达: " + e.getMessage());
            result.setSource("unavailable");
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    /**
     * 健康检查（worker /actuator/health）。
     */
    public boolean isWorkerHealthy() {
        try (Response resp = client().newCall(
                new Request.Builder().url(workerBaseUrl + "/actuator/health").get().build()
        ).execute()) {
            return resp.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
