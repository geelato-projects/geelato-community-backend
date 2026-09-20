package cn.geelato.web.common.security.delegate;

import cn.geelato.utils.LocalBoundedCache;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 进程内内存实现的委托代办会话存储。
 * <p>
 * TTL 与 {@code DefaultSecurityInterceptor.tokenContextCache} 保持一致（30 分钟），
 * 过期与容量护栏由 {@link LocalBoundedCache} 统一托管。仅适用于单实例部署；
 * 多实例部署请提供 Redis 实现覆盖本 Bean。
 * <p>
 * 默认通过 {@link DelegateAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 注册；
 * 不在此类上直接使用 {@code @Component + @ConditionalOnMissingBean}（该组合条件求值不可靠）。
 *
 * @author geelato
 */
@Slf4j
public class InMemoryDelegateSessionStore implements DelegateSessionStore {

    /** 会话有效期：30 分钟，与 tokenContextCache 一致 */
    private static final long TTL_MILLIS = 30 * 60 * 1000L;

    private final LocalBoundedCache<String, DelegateSession> store = new LocalBoundedCache<>("delegate-session", TTL_MILLIS, 100_000);

    @Override
    public DelegateSession get(String tokenKey) {
        if (tokenKey == null || tokenKey.isEmpty()) {
            return null;
        }
        return store.get(tokenKey);
    }

    @Override
    public void put(String tokenKey, DelegateSession session) {
        if (tokenKey == null || tokenKey.isEmpty() || session == null) {
            return;
        }
        session.setCreateAt(System.currentTimeMillis());
        session.setExpireAt(System.currentTimeMillis() + TTL_MILLIS);
        store.put(tokenKey, session);
    }

    @Override
    public void remove(String tokenKey) {
        if (tokenKey == null || tokenKey.isEmpty()) {
            return;
        }
        store.remove(tokenKey);
    }

    @Override
    public void removeByOriginUser(String originUserId) {
        if (originUserId == null || originUserId.isEmpty()) {
            return;
        }
        store.removeIf((tokenKey, session) -> originUserId.equals(session.getOriginUserId()));
    }

    @Override
    public Collection<DelegateSession> queryByOriginUser(String originUserId) {
        List<DelegateSession> result = new ArrayList<>();
        if (originUserId == null || originUserId.isEmpty()) {
            return result;
        }
        for (DelegateSession session : store.values()) {
            if (originUserId.equals(session.getOriginUserId())) {
                result.add(session);
            }
        }
        return result;
    }
}
