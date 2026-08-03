package cn.geelato.plugin;

import org.pf4j.ExtensionPoint;

/**
 * geelato 插件扩展点标记接口。
 * <p>所有业务扩展点应继承本接口（而非直接继承 {@link ExtensionPoint}），
 * 以统一 geelato 插件体系的扩展点约定。</p>
 *
 * @author geelato
 */
public interface PluginExtensionPoint extends ExtensionPoint {

    /**
     * 扩展可用性健康检查。
     * <p>默认返回 true（兼容现有插件）。插件可覆写该方法做真实探测，
     * 例如 OCR 插件检测模型是否就绪、远程依赖是否可达。
     * {@code /pm/list} 会调用本方法以反映扩展的真实可用性，
     * 区别于 pf4j 的 {@code STARTED}（仅表示插件已加载）。</p>
     *
     * @return true 表示扩展可用
     */
    default boolean healthCheck() {
        return true;
    }
}
