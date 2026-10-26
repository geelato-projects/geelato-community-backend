package cn.geelato.ide.debug;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * 调试代理握手拦截器：在浏览器 ws 升级前按 {@code token} 校验会话归属并解析内网 inspector URI。
 * <p>
 * 校验失败（无登记 / token 不符 / 会话非本人）→ 拒绝握手（返回 false，HTTP 403）。校验通过后把
 * {@code inspectorUri}/{@code sessionId} 放入 ws 会话属性，供 {@link ScriptDebugWebSocketProxyHandler} 连接内网。
 * 归属既由一次性 token 绑定（token 仅在本人 {@code /debug/start} 时签发并回发），也二次核对当前登录用户，
 * 双保险防越权连他人 inspector。
 *
 * @author geelato
 */
public class DebugHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_INSPECTOR_URI = "glDebugInspectorUri";
    public static final String ATTR_SESSION_ID = "glDebugSessionId";

    private final DebugSessionRegistry registry;

    public DebugHandshakeInterceptor(DebugSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String token = queryParam(uri, "token");
        String sessionId = parseSessionId(uri.getPath());
        DebugSessionRegistry.Registration reg = registry.get(sessionId);
        if (reg == null || token == null || !token.equals(reg.getToken())) {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_INSPECTOR_URI, reg.getInspectorUri());
        attributes.put(ATTR_SESSION_ID, sessionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /** 取路径末段作为 sessionId：.../ws/{sessionId}。 */
    private static String parseSessionId(String path) {
        if (path == null) {
            return null;
        }
        int q = path.indexOf('?');
        String p = q >= 0 ? path.substring(0, q) : path;
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static String queryParam(URI uri, String name) {
        String raw = uri.getQuery();
        if (raw == null) {
            return null;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
