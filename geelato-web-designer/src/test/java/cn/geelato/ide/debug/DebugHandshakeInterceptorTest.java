package cn.geelato.ide.debug;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Same-origin debug proxy ownership guard: the handshake must accept only the
 * session whose id + one-shot token match, and reject everyone else (403), so a
 * foreign sessionId/token can never reach the internal inspector.
 */
class DebugHandshakeInterceptorTest {

    private final DebugSessionRegistry registry = new DebugSessionRegistry();
    private final DebugHandshakeInterceptor interceptor = new DebugHandshakeInterceptor(registry);

    @Test
    void registryRoundTripAndTokenLookup() {
        DebugSessionRegistry.Registration reg =
                registry.register("s-1", "ws://127.0.0.1:9000/s-1", "alice", "tok-1");
        assertEquals("s-1", reg.getSessionId());
        assertNotNull(registry.get("s-1"));
        assertNotNull(registry.findByToken("tok-1"));
        assertNull(registry.findByToken("nope"));
        assertNull(registry.findByToken(null));
        assertNotNull(registry.remove("s-1"));
        assertNull(registry.get("s-1"));
    }

    @Test
    void acceptsHandshakeWhenOwnerTokenMatches() {
        registry.register("s-1", "ws://127.0.0.1:9000/s-1", "alice", "tok-1");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("/api/script/debugger/ws/s-1?token=tok-1"));

        Map<String, Object> attributes = new HashMap<>();
        boolean ok = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertTrue(ok);
        assertEquals("ws://127.0.0.1:9000/s-1", attributes.get(DebugHandshakeInterceptor.ATTR_INSPECTOR_URI));
        assertEquals("s-1", attributes.get(DebugHandshakeInterceptor.ATTR_SESSION_ID));
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void rejectsHandshakeWhenTokenMismatch() {
        registry.register("s-1", "ws://127.0.0.1:9000/s-1", "alice", "tok-1");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("/api/script/debugger/ws/s-1?token=wrong"));

        boolean ok = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(ok);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsHandshakeForUnknownSession() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("/api/script/debugger/ws/ghost?token=whatever"));

        boolean ok = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(ok);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }
}
