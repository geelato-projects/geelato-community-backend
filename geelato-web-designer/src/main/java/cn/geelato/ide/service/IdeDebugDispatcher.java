package cn.geelato.ide.service;

import cn.geelato.ide.dto.IdeDebugRequest;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * web-designer → script-worker 调试会话 HTTP 调度器（镜像 {@link IdeWorkerDispatcher} 的 dry-run 通道）。
 * <p>
 * 调试 Context 恒在 worker JVM 构建（执行/inspector 端口所在），本调度器仅经内网 HTTP 委托：
 * start 回内网 inspector {@code host:port/path}（供同源代理连接，绝不下发浏览器），
 * 并按 workerBaseUrl 主机换算可达 inspector 主机（默认与 worker 同机时即回环）。
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeDebugDispatcher {

    @Value("${geelato.ide.worker.baseUrl:http://localhost:8087}")
    private String workerBaseUrl;

    /** inspector 可达主机覆盖（worker 与 BFF 跨机部署时置为 worker 可达地址；空=沿用 workerBaseUrl 主机）。 */
    @Value("${geelato.ide.worker.debugInspectorHost:}")
    private String debugInspectorHostOverride;

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

    /** worker 调试会话坐标（内网可见；host 已换算为 BFF 可达 inspector 主机）。 */
    @Getter
    public static final class DebugTarget {
        private final String sessionId;
        private final String inspectorHost;
        private final int inspectorPort;
        private final String path;
        private final String owner;
        private final String engineVersion;

        DebugTarget(String sessionId, String inspectorHost, int inspectorPort, String path,
                    String owner, String engineVersion) {
            this.sessionId = sessionId;
            this.inspectorHost = inspectorHost;
            this.inspectorPort = inspectorPort;
            this.path = path;
            this.owner = owner;
            this.engineVersion = engineVersion;
        }

        /** 拼内网 inspector ws 绝对 URI。 */
        public String inspectorWsUri() {
            String p = (path == null || path.isBlank()) ? sessionId : path;
            return "ws://" + inspectorHost + ":" + inspectorPort + "/" + p;
        }
    }

    public DebugTarget start(IdeDebugRequest request, String owner) {
        JSONObject body = new JSONObject();
        body.put("language", (request.getLanguage() == null || request.getLanguage().isBlank()) ? "js" : request.getLanguage());
        body.put("content", request.getContent());
        body.put("scriptName", request.getScriptName());
        body.put("parameter", request.getParameter());
        body.put("bindings", request.getBindings());
        body.put("profileId", request.getProfileId());
        body.put("engineVersion", request.getEngineVersion());
        body.put("timeoutMs", request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000L);
        body.put("debugOwner", owner);

        Request.Builder rb = new Request.Builder()
                .url(workerBaseUrl + "/script/debug/start")
                .post(RequestBody.create(body.toJSONString(), JSON_MT));
        appendIdentityHeaders(rb);

        try (Response resp = client().newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("worker debug HTTP " + resp.code() + ": " + truncate(respBody, 300));
            }
            JSONObject json = JSON.parseObject(respBody);
            String reportedHost = json.getString("host");
            String reachableHost = resolveInspectorHost(reportedHost);
            return new DebugTarget(
                    json.getString("sessionId"),
                    reachableHost,
                    json.getIntValue("port"),
                    json.getString("path"),
                    owner,
                    json.getString("engineVersion"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("worker 不可达（debug start）: " + e.getMessage(), e);
        }
    }

    public void stop(String sessionId) {
        Request.Builder rb = new Request.Builder()
                .url(workerBaseUrl + "/script/debug/stop/" + sessionId)
                .post(RequestBody.create("{}", JSON_MT));
        appendIdentityHeaders(rb);
        try (Response resp = client().newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                log.warn("worker debug stop 非 2xx: sessionId={} code={}", sessionId, resp.code());
            }
        } catch (java.io.IOException e) {
            log.warn("worker debug stop 失败: sessionId={} {}", sessionId, e.getMessage());
        }
    }

    public JSONObject status(String sessionId) {
        Request.Builder rb = new Request.Builder()
                .url(workerBaseUrl + "/script/debug/" + sessionId + "/status").get();
        appendIdentityHeaders(rb);
        try (Response resp = client().newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                JSONObject err = new JSONObject();
                err.put("alive", false);
                err.put("state", "STOPPED");
                err.put("error", true);
                err.put("errorMessage", "worker HTTP " + resp.code());
                return err;
            }
            return JSON.parseObject(respBody);
        } catch (java.io.IOException e) {
            JSONObject err = new JSONObject();
            err.put("alive", false);
            err.put("state", "STOPPED");
            err.put("error", true);
            err.put("errorMessage", "worker 不可达: " + e.getMessage());
            return err;
        }
    }

    /** inspector host 换算：worker 报告回环时，沿用 workerBaseUrl 主机（跨机部署可显式覆盖）。 */
    private String resolveInspectorHost(String reportedHost) {
        if (Strings.isNotBlank(debugInspectorHostOverride)) {
            return debugInspectorHostOverride.trim();
        }
        boolean loopback = reportedHost == null || reportedHost.isBlank()
                || reportedHost.startsWith("127.") || "localhost".equalsIgnoreCase(reportedHost)
                || "::1".equals(reportedHost);
        if (loopback) {
            String workerHost = hostOf(workerBaseUrl);
            if (Strings.isNotBlank(workerHost) && !isLoopbackHost(workerHost)) {
                // worker 与 BFF 跨机：回环不可达，改用 workerBaseUrl 主机（须 worker inspector 可被该网卡接受）
                return workerHost;
            }
        }
        return reportedHost == null || reportedHost.isBlank() ? "127.0.0.1" : reportedHost;
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host == null || host.startsWith("127.") || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private void appendIdentityHeaders(Request.Builder rb) {
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
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
