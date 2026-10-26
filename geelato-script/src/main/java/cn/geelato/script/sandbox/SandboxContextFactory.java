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
        return baseBuilder(languages, statementLimit, esVersion).build();
    }

    /**
     * 调试专用 Context：在正常沙箱配置基础上追加 GraalJS 内置 CDP inspector。
     * <ul>
     *   <li>{@code inspect=host:port}：inspector ws 服务只绑回环（host 缺省 127.0.0.1），绝不公网暴露；</li>
     *   <li>{@code inspect.Suspend=true} + {@code inspect.WaitAttached=true}：未连上 DevTools 前不执行（挂起等接）；</li>
     *   <li>{@code inspect.Path=sessionId}：多会话经路径区分。</li>
     * </ul>
     * 依赖 {@code allowExperimentalOptions(true)}（baseBuilder 已设）。调用方须自行保证 port 未被占用（
     * 传 0 则由实现自动分配并回读实际端口）。statementLimit 仍生效防死循环。
     */
    public Context createForDebug(String[] languages, long statementLimit, String esVersion,
                                  String inspectHost, int inspectPort, String inspectPath) {
        Context.Builder builder = baseBuilder(languages, statementLimit, esVersion);
        String host = inspectHost == null || inspectHost.isBlank() ? "127.0.0.1" : inspectHost;
        builder.option("inspect", host + ":" + Math.max(0, inspectPort));
        builder.option("inspect.Suspend", "true");
        builder.option("inspect.WaitAttached", "true");
        if (inspectPath != null && !inspectPath.isBlank()) {
            builder.option("inspect.Path", inspectPath);
        }
        return builder.build();
    }

    /** 沙箱基线配置（正常与调试共用，保证 kit/bindings/资源限制注入等价）。 */
    private Context.Builder baseBuilder(String[] languages, long statementLimit, String esVersion) {
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
        return builder;
    }

    private static Engine buildEngine() {
        return Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build();
    }

    private static String engineKey(String[] languages, String esVersion) {
        return String.join(",", languages) + "|" + (esVersion == null ? "" : esVersion);
    }
}
