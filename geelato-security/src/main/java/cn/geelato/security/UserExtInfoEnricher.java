package cn.geelato.security;

/**
 * 用户扩展属性补齐器：由上层实现，在用户快照装载时填充 {@link User#getExtendInfo()}。
 * 数据来源（绑定表/配置/认证中心）由上层决定；键约定由消费方（git 操作模块等）定义。
 */
public interface UserExtInfoEnricher {

    void enrich(User user);
}
