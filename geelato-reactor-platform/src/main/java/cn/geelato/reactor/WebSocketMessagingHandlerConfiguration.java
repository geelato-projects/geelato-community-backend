package cn.geelato.reactor;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

//@Configuration
public class WebSocketMessagingHandlerConfiguration implements WebSocketConfigurer {
//    @Autowired
    private ApplicationContext applicationContext;

    private final Map<String, TextWebSocketHandler> handlerMap = new LinkedHashMap<>();

    @Override
    public void registerWebSocketHandlers(@Nonnull WebSocketHandlerRegistry registry) {
        Map<String, TextWebSocketHandler> webSocketHandlers = getWebSocketHandlers();
        for (Map.Entry<String, TextWebSocketHandler> entry : webSocketHandlers.entrySet()) {
            String path = entry.getKey();
            TextWebSocketHandler handler = entry.getValue();
            registry.addHandler(handler, path).setAllowedOrigins("*");
        }

    }

    private Map<String, TextWebSocketHandler> getWebSocketHandlers() {
        Set<Class<?>> annotatedClasses = getAnnotatedClasses();
        return annotatedClasses.stream()
                .filter(TextWebSocketHandler.class::isAssignableFrom)
                .collect(Collectors.toMap(
                        clazz -> clazz.getAnnotation(WebSocketMapping.class).value(),
                        clazz -> {
                            try {
                                return (TextWebSocketHandler) clazz.getDeclaredConstructor().newInstance();
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to instantiate WebSocketHandler", e);
                            }
                        }
                ));
    }
    private Set<Class<?>> getAnnotatedClasses() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        return java.util.stream.Stream.of(beanNames)
                .map(beanName -> applicationContext.getType(beanName))
                .filter(clazz -> clazz != null && clazz.isAnnotationPresent(WebSocketMapping.class))
                .collect(Collectors.toSet());
    }
}
