package cn.geelato.script.kit;

/**
 * scoped kit 工厂：消费方 kit 带外部依赖（Dao 等）时用工厂注册，SDK 每执行 create 一次，
 * kit 经 KitContext 取执行级属性（attributes）。
 */
@FunctionalInterface
public interface ScopedKitFactory {

    Object create(KitContext ctx);
}
