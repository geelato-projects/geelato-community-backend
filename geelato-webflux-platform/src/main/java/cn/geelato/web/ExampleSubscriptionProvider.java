package cn.geelato.web;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ExampleSubscriptionProvider implements SubscriptionProvider{
    @Override
    public String name() {
        return "example_subscription";
    }

    @Override
    public Flux<?> subscribe(String request) {
        return null;
    }
}
