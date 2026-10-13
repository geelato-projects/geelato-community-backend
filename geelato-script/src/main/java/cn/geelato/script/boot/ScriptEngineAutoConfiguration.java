package cn.geelato.script.boot;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(org.graalvm.polyglot.Context.class)
@EnableConfigurationProperties(ScriptEngineProperties.class)
public class ScriptEngineAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ScriptEngine.class)
    public DefaultScriptEngine scriptEngine(ScriptEngineProperties properties,
                                            ObjectProvider<ScriptEngineCustomizer> customizers) {
        ScriptEngineBuilder builder = new ScriptEngineBuilder()
                .statementLimit(properties.getStatementLimit())
                .workerPoolSize(properties.getWorkerPoolSize())
                .pythonEnabled(properties.isPythonEnabled())
                .engineShared(properties.isEngineShared())
                .defaultVersion(properties.getDefaultVersion())
                .defaultEsVersion(properties.getDefaultEsVersion())
                .sqlRowLimit(properties.getSql().getRowLimit())
                .sqlQueryTimeoutMs(properties.getSql().getQueryTimeoutMs())
                .httpEnabled(properties.getHttp().isEnabled())
                .httpAllowlist(properties.getHttp().getAllowlist())
                .httpTimeoutMs(properties.getHttp().getTimeoutMs())
                .encryptKey(properties.getEncrypt().getAesKey())
                .daoWriteEnabled(properties.getDao().isWriteEnabled())
                .daoDryRun(properties.getDao().isDryRun());
        for (String pkg : properties.getScanPackages()) {
            builder.scanPackage(pkg);
        }
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }
}
