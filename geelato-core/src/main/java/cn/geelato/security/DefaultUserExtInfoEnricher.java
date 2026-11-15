package cn.geelato.security;

/**
 * 默认空实现：上层未提供填充器时的缺省态（用户未绑定扩展信息）。
 */
public class DefaultUserExtInfoEnricher implements UserExtInfoEnricher {

    @Override
    public void enrich(User user) {
        // no-op
    }
}
