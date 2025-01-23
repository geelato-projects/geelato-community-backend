package cn.geelato.webflux;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultMessagingManager implements MessagingManager {

    private final Map<String, SubscriptionProvider> subscriptionProvider = new ConcurrentHashMap<>();

    private final static PathMatcher matcher = new AntPathMatcher();
    @Override
    public Flux<String> subscribe(String request) {
        return null;
    }
}
