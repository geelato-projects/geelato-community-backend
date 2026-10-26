package cn.geelato.ide.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 调试同源 WebSocket 代理：把浏览器 DevTools 的 CDP 帧双向转发到内网 worker inspector。
 * <p>
 * 浏览器侧走 Spring {@link WebSocketHandler}（升级握手已由 {@link DebugHandshakeInterceptor} 校验归属并注入
 * 内网 {@code inspectorUri}）；worker 侧用 JDK {@link java.net.http.WebSocket}（无额外容器依赖）。CDP 为文本帧，
 * 分片按 {@code last} 聚合后原样转发；浏览器→inspector 在连接就绪前入队、就绪后冲刷。任一侧关闭/出错即联动关闭另一侧。
 * inspector 端口只绑回环，仅本代理（与 worker 同机时经 127.0.0.1）可达，绝不公网暴露。
 *
 * @author geelato
 */
@Slf4j
public class ScriptDebugWebSocketProxyHandler implements WebSocketHandler {

    private static final String ATTR_UPSTREAM = "glDebugUpstream";
    private static final String ATTR_OPEN = "glDebugUpstreamOpen";
    private static final String ATTR_QUEUE = "glDebugUpstreamQueue";
    private static final String ATTR_LOCK = "glDebugBrowserSendLock";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void afterConnectionEstablished(WebSocketSession browser) throws Exception {
        String inspectorUri = (String) browser.getAttributes().get(DebugHandshakeInterceptor.ATTR_INSPECTOR_URI);
        if (inspectorUri == null) {
            browser.close(CloseStatus.NOT_ACCEPTABLE.withReason("no inspector target"));
            return;
        }
        AtomicBoolean open = new AtomicBoolean(false);
        Deque<String> queue = new ArrayDeque<>();
        browser.getAttributes().put(ATTR_OPEN, open);
        browser.getAttributes().put(ATTR_QUEUE, queue);
        browser.getAttributes().put(ATTR_LOCK, new Object());

        WebSocket upstream = httpClient.newWebSocketBuilder().buildAsync(URI.create(inspectorUri), new WebSocket.Listener() {
            private final StringBuilder textBuf = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                textBuf.append(data);
                if (last) {
                    String message = textBuf.toString();
                    textBuf.setLength(0);
                    sendToBrowser(browser, message);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                // CDP 通常纯文本；如遇二进制帧原样转交浏览器。
                sendToBrowserBinary(browser, data);
                ws.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                log.warn("debug inspector upstream error: {} {}", inspectorUri, error.getMessage());
                closeBrowser(browser);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                closeBrowser(browser);
                return null;
            }

            @Override
            public void onOpen(WebSocket ws) {
                open.set(true);
                ws.request(1);
                // 冲刷浏览器在连接就绪前积压的帧。
                synchronized (queue) {
                    String pending;
                    while ((pending = queue.pollFirst()) != null) {
                        ws.sendText(pending, true);
                    }
                }
            }
        }).join();
        browser.getAttributes().put(ATTR_UPSTREAM, upstream);
    }

    @Override
    public void handleMessage(WebSocketSession browser, WebSocketMessage<?> message) {
        WebSocket upstream = (WebSocket) browser.getAttributes().get(ATTR_UPSTREAM);
        if (upstream == null) {
            return;
        }
        AtomicBoolean open = (AtomicBoolean) browser.getAttributes().get(ATTR_OPEN);
        Deque<String> queue = castQueue(browser);
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            if (open != null && open.get()) {
                upstream.sendText(payload, true);
            } else if (queue != null) {
                synchronized (queue) {
                    queue.addLast(payload);
                }
            }
        } else if (message instanceof BinaryMessage) {
            ByteBuffer buf = ((BinaryMessage) message).getPayload();
            if (open != null && open.get()) {
                upstream.sendBinary(buf, true);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession browser, Throwable exception) {
        log.warn("debug browser session error: {}", exception.getMessage());
        closeUpstream(browser);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browser, CloseStatus closeStatus) {
        closeUpstream(browser);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void sendToBrowser(WebSocketSession browser, String message) {
        if (!browser.isOpen()) {
            return;
        }
        Object lock = browser.getAttributes().get(ATTR_LOCK);
        synchronized (lock == null ? browser : lock) {
            try {
                browser.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.debug("forward CDP to browser failed: {}", e.getMessage());
            }
        }
    }

    private void sendToBrowserBinary(WebSocketSession browser, ByteBuffer data) {
        if (!browser.isOpen()) {
            return;
        }
        Object lock = browser.getAttributes().get(ATTR_LOCK);
        synchronized (lock == null ? browser : lock) {
            try {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                browser.sendMessage(new BinaryMessage(bytes));
            } catch (Exception e) {
                log.debug("forward binary to browser failed: {}", e.getMessage());
            }
        }
    }

    private void closeBrowser(WebSocketSession browser) {
        try {
            if (browser.isOpen()) {
                browser.close(CloseStatus.NORMAL);
            }
        } catch (Exception ignored) {
        }
    }

    private void closeUpstream(WebSocketSession browser) {
        WebSocket upstream = (WebSocket) browser.getAttributes().get(ATTR_UPSTREAM);
        if (upstream != null) {
            try {
                upstream.sendClose(WebSocket.NORMAL_CLOSURE, "browser closed");
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Deque<String> castQueue(WebSocketSession browser) {
        return (Deque<String>) browser.getAttributes().get(ATTR_QUEUE);
    }
}
