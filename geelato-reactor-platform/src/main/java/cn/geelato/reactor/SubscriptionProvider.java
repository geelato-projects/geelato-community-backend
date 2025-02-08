package cn.geelato.reactor;

import reactor.core.publisher.Flux;

public interface SubscriptionProvider {

    String name();
    Flux<?> subscribe(String request);
}
