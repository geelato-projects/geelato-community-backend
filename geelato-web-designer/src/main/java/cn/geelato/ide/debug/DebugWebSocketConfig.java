package cn.geelato.ide.debug;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 调试同源 WebSocket 代理装配：把 {@code /api/script/debugger/ws/*} 交给
 * {@link ScriptDebugWebSocketProxyHandler}，握手经 {@link DebugHandshakeInterceptor} 校验归属。
 *
 * @author geelato
 */
@Configuration
@EnableWebSocket
public class DebugWebSocketConfig implements WebSocketConfigurer {

    private final DebugSessionRegistry registry;

    public DebugWebSocketConfig(DebugSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry handlers) {
        handlers.addHandler(new ScriptDebugWebSocketProxyHandler(), "/api/script/debugger/ws/*")
                .addInterceptors(new DebugHandshakeInterceptor(registry))
                .setAllowedOriginPatterns("*");
    }
}
