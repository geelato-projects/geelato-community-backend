package cn.geelato.web;



import reactor.core.publisher.Flux;
public interface MessagingManager {
    Flux<String> subscribe(String request);
}
