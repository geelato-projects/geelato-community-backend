package cn.geelato.webflux;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static cn.geelato.webflux.DataOp.result;
import static cn.geelato.webflux.DataOp.source;

@Component
@WebSocketMapping("/messaging/example")
public class WebSocketMessagingHandler implements WebSocketHandler {

    private final MessagingManager messagingManager;

    public WebSocketMessagingHandler(MessagingManager messagingManager) {
        this.messagingManager = messagingManager;
        addData().subscribe();
    }

    @Override
    @Nonnull
    @RequestMapping
    public Mono<Void> handle(@Nonnull WebSocketSession session) {
        session.receive().subscribe(System.out::println);
        return session.send(initData(session));
    }

    public Flux<WebSocketMessage> initData(WebSocketSession session ){
        return DataOp.source.map(session::textMessage);

    }

    public Flux<Void> addData(){
        System.out.println("添加数据");
        result = Flux.push(this::createData);
        source = Flux.push(this::generateData);
        return Flux.empty();
    }

    private void generateData(FluxSink<String> sink){
        result.doOnNext(sink::next)
                .takeWhile(str -> !sink.isCancelled())
                .subscribe();
    }

    private void createData(FluxSink<String> sink){
        Flux.interval(Duration.ofSeconds(5)).map(i-> "Flux data---"+ i)
                .doOnNext(sink::next)
                .subscribe();
    }
}
