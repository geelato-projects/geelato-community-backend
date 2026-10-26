package cn.geelato.ide.debug;

import lombok.Getter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调试代理会话登记表（studio BFF 进程内）。
 * <p>
 * {@code /api/script/debug/start} 成功后登记 {@code sessionId -> {内网 inspector ws URI, owner, 代理 token}}，
 * 供同源 WebSocket 代理在握手时按 token 校验归属、并连接内网 inspector；{@code stop} 时移除。
 * 内网 host:port 仅存活于 BFF 进程，绝不下发浏览器。
 *
 * @author geelato
 */
@Component
public class DebugSessionRegistry {

    /** 单条代理登记。 */
    @Getter
    @ToString(exclude = "token")
    public static final class Registration {
        private final String sessionId;
        /** 内网 inspector ws 绝对 URI（ws://host:port/path），仅 BFF 内部使用。 */
        private final String inspectorUri;
        /** 会话归属（平台用户 id）。 */
        private final String owner;
        /** 一次性代理 token（握手校验，绑定会话与 owner）。 */
        private final String token;

        Registration(String sessionId, String inspectorUri, String owner, String token) {
            this.sessionId = sessionId;
            this.inspectorUri = inspectorUri;
            this.owner = owner;
            this.token = token;
        }
    }

    private final Map<String, Registration> bySessionId = new ConcurrentHashMap<>();

    public Registration register(String sessionId, String inspectorUri, String owner, String token) {
        Registration reg = new Registration(sessionId, inspectorUri, owner, token);
        bySessionId.put(sessionId, reg);
        return reg;
    }

    public Registration get(String sessionId) {
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    /** 按 token 取登记（握手校验用）。 */
    public Registration findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        for (Registration reg : bySessionId.values()) {
            if (reg.token.equals(token)) {
                return reg;
            }
        }
        return null;
    }

    public Registration remove(String sessionId) {
        return sessionId == null ? null : bySessionId.remove(sessionId);
    }
}
