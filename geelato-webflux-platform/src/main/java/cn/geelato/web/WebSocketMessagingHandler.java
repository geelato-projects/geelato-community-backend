package cn.geelato.web;

import jakarta.annotation.Nonnull;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class WebSocketMessagingHandler implements WebSocketHandler {

    private final MessagingManager messagingManager;

    public WebSocketMessagingHandler(MessagingManager messagingManager) {
        this.messagingManager = messagingManager;
    }

    @Override
    @Nonnull
    public Mono<Void> handle(@Nonnull WebSocketSession session) {
        session.receive().subscribe(System.out::println);
        return session.send(initData(session));
    }

    public Flux<WebSocketMessage> initData(WebSocketSession session ){
        return DataOp.source.map(session::textMessage);

    }
}
