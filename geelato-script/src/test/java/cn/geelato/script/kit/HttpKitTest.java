package cn.geelato.script.kit;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpKitTest {

    private static HttpServer server;
    private static String baseUrl;
    private static DefaultScriptEngine engine;
    private static DefaultScriptEngine disabledEngine;
    private static DefaultScriptEngine foreignAllowlistEngine;

    @BeforeAll
    static void init() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String payload = "{\"method\": \"" + exchange.getRequestMethod() + "\", \"body\": \"" + body + "\","
                    + "\"query\": \"" + exchange.getRequestURI().getRawQuery() + "\","
                    + "\"accept\": \"" + exchange.getRequestHeaders().getFirst("X-Test") + "\"}";
            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";

        engine = builder(true, List.of("127.0.0.1")).build();
        disabledEngine = builder(false, List.of("127.0.0.1")).build();
        foreignAllowlistEngine = builder(true, List.of("api.example.com")).build();
    }

    private static ScriptEngineBuilder builder(boolean enabled, List<String> allowlist) {
        return new ScriptEngineBuilder()
                .httpEnabled(enabled)
                .httpAllowlist(allowlist)
                .httpTimeoutMs(3_000)
                .profile(ScriptProfile.builder()
                        .id("default").namespaces(Set.of(ScriptProfile.ALL)).variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(5_000).build());
    }

    @AfterAll
    static void shutdown() {
        engine.close();
        disabledEngine.close();
        foreignAllowlistEngine.close();
        server.stop(0);
    }

    private static ScriptResult run(DefaultScriptEngine engine, String script) {
        return engine.execute(ScriptExecution.builder().content(script).build());
    }

    @Test
    void getWithParamsAndHeaders() {
        ScriptResult result = run(engine, "const o = JSON.parse($gl.http.get('" + baseUrl + "', {a: 1, k: 'x y'}, {'X-Test': 'yes'}));"
                + " return o.method + '/' + o.accept + '/' + o.query");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("GET/yes/a=1&k=x+y", result.getValue());
    }

    @Test
    void postJsonBody() {
        ScriptResult result = run(engine, "return $gl.http.post('" + baseUrl + "', {name: '甲', n: 1}, {})");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertTrue(String.valueOf(result.getValue()).contains("甲"), String.valueOf(result.getValue()));
    }

    @Test
    void requestReturnsFullResponse() {
        ScriptResult result = run(engine, "const r = $gl.http.request({method: 'PUT', url: '" + baseUrl + "', body: 'raw-body'});"
                + " const b = JSON.parse(r.body);"
                + " return r.status + '/' + b.method + '/' + b.body + '/' + r.headers['content-type']");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("200/PUT/raw-body/application/json", result.getValue());
    }

    @Test
    void disabledThrowsPrecisely() {
        ScriptResult result = run(disabledEngine, "return $gl.http.get('" + baseUrl + "', {}, {})");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("HTTP 出网未启用"), result.getError().getMessage());
    }

    @Test
    void allowlistRejectsForeignHost() {
        ScriptResult result = run(foreignAllowlistEngine, "return $gl.http.get('" + baseUrl + "', {}, {})");
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("不在白名单内"), result.getError().getMessage());
    }

    @Test
    void wildcardAllowlistDoesNotLeakLocalhost() {
        DefaultScriptEngine wildcardEngine = builder(true, List.of("*.example.com")).build();
        try {
            ScriptResult result = run(wildcardEngine, "return $gl.http.get('" + baseUrl + "', {}, {})");
            assertEquals("ERROR", result.getStatus());
            assertTrue(result.getError().getMessage().contains("不在白名单内"), result.getError().getMessage());
        } finally {
            wildcardEngine.close();
        }
    }
}
