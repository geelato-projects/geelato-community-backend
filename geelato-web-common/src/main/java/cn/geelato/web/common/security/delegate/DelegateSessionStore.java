package cn.geelato.web.common.security.delegate;

import java.util.Collection;

/**
 * 委托代办会话存储 SPI。
 * <p>
 * key 约定为完整的原始 Authorization 头字符串（如 {@code "Bearer xxx"} / {@code "JWTBearer yyy"}），
 * 与 {@code DefaultSecurityInterceptor.tokenContextCache} 的 key 保持一致。
 * <p>
 * 默认实现为进程内内存（{@link InMemoryDelegateSessionStore}）；
 * 多实例部署可提供 Redis 实现替换默认 Bean，使委托代办态跨节点一致。
 *
 * @author geelato
 */
public interface DelegateSessionStore {

    /**
     * 获取指定凭证 key 当前的委托代办会话；不存在或已过期返回 null。
     */
    DelegateSession get(String tokenKey);

    /**
     * 写入委托代办会话。实现应自行设置 TTL（与 tokenContextCache 一致，默认 30 分钟）。
     */
    void put(String tokenKey, DelegateSession session);

    /**
     * 移除指定凭证 key 的委托代办会话（退出代办）。
     */
    void remove(String tokenKey);

    /**
     * 移除某实际操作人（导师）发起的全部委托代办会话。
     * 用于导师被禁用 / 全部委托关系失效等场景。
     */
    void removeByOriginUser(String originUserId);

    /**
     * 返回某实际操作人（导师）当前仍有效的委托代办会话（用于查询 / 踢出）。
     */
    Collection<DelegateSession> queryByOriginUser(String originUserId);
}
