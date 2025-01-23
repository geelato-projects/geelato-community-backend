package cn.geelato.webflux;



import reactor.core.publisher.Flux;
public interface MessagingManager {
    Flux<String> subscribe(String request);
}
