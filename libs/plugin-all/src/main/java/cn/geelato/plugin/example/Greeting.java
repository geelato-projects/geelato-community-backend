package cn.geelato.plugin.example;

import cn.geelato.plugin.PluginExtensionPoint;

/**
 * 示例扩展点。
 *
 * @author geelato
 */
public interface Greeting extends PluginExtensionPoint {

    String getGreeting();
}
