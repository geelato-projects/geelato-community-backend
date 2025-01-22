package cn.geelato.web;

import reactor.core.publisher.Flux;

public interface SubscriptionProvider {

    String name();
    Flux<?> subscribe(String request);
}
