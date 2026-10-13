package cn.geelato.script.sandbox;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.graal.GraalVariable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙箱 Context 工厂（同源移植 script-worker 收口，未用其 HostAccess.ALL 反面配置）：
 * HostAccess 仅注解白名单 + deny 危险类；类查找白名单；IO/线程/进程/native/env 全禁；
 * ResourceLimits 语句数上限防死循环；共享 Engine 按 esVersion 分桶复用编译缓存。
 */
public class SandboxContextFactory {

    private static final Set<String> HOST_CLASS_ALLOWLIST = Set.of(
            "cn.geelato.core.graal.GraalService",
            "cn.geelato.core.graal.GraalFunction",
            "cn.geelato.core.graal.GraalVariable",
            "java.util.HashMap",
            "java.util.ArrayList",
            "java.util.LinkedHashMap",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.math.BigDecimal",
            "java.util.Date");

    private static final HostAccess SANDBOX_HOST = HostAccess.newBuilder()
            .allowAccessAnnotatedBy(GraalFunction.class)
            .allowImplementationsAnnotatedBy(GraalService.class)
            .denyAccess(Class.class)
            .denyAccess(ClassLoader.class)
            .denyAccess(System.class)
            .denyAccess(Runtime.class)
            .denyAccess(Thread.class)
            .build();

    private final boolean engineShared;
    private final Map<String, Engine> sharedEngines = new ConcurrentHashMap<>();

    public SandboxContextFactory(boolean engineShared) {
        this.engineShared = engineShared;
    }

    public Context create(String[] languages, long statementLimit, String esVersion) {
        ResourceLimits limits = ResourceLimits.newBuilder().statementLimit(statementLimit, null).build();
        Context.Builder builder = Context.newBuilder(languages)
                .allowHostAccess(SANDBOX_HOST)
                .allowHostClassLookup(HOST_CLASS_ALLOWLIST::contains).allowAllAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowExperimentalOptions(true)
                .resourceLimits(limits);
        if (esVersion != null && !esVersion.isBlank()) {
            builder.option("js.ecmascript-version", esVersion);
        }
        if (engineShared) {
            builder.engine(sharedEngines.computeIfAbsent(engineKey(languages, esVersion), k -> buildEngine()));
        } else {
            builder.option("engine.WarnInterpreterOnly", "false");
        }
        return builder.build();
    }

    private static Engine buildEngine() {
        return Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build();
    }

    private static String engineKey(String[] languages, String esVersion) {
        return String.join(",", languages) + "|" + (esVersion == null ? "" : esVersion);
    }
}
