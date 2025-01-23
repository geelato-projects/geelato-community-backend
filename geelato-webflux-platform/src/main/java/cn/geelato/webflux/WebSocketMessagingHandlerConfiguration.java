package cn.geelato.webflux;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class WebSocketMessagingHandlerConfiguration{

    @Bean
    public HandlerMapping webSocketMessagingHandlerMapping() {
        return new WebSocketMappingHandlerMapping();
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
