package cn.geelato.script.boot;

import cn.geelato.script.core.ScriptEngineBuilder;

/**
 * 消费方引擎定制点：注册专属 kit / profile / revision / 扫描包 / 数据源装配。
 */
@FunctionalInterface
public interface ScriptEngineCustomizer {

    void customize(ScriptEngineBuilder builder);
}
