package cn.geelato.script.kit;

/**
 * scoped kit 版本化槽位：kitClass + 可选工厂。挂在 EngineRevision 快照上，
 * 使每个引擎版本可独立更换 scoped kit 实现，不回退全局注册表。
 */
public record ScopedKitSlot(Class<?> kitClass, ScopedKitFactory factory) {
}
