package cn.geelato.script.core;

import cn.geelato.script.kit.DaoSupplier;
import cn.geelato.script.kit.DataSourceSupplier;
import cn.geelato.script.kit.IdentitySupplier;
import cn.geelato.script.kit.KitRegistry;
import cn.geelato.script.kit.OrmExecutionWrapper;
import cn.geelato.script.lang.JsLanguageExecutor;
import cn.geelato.script.lang.ScriptLanguageExecutor;
import cn.geelato.script.sandbox.SandboxContextFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引擎构建器（消费方定制入口）：注册 kit（实例 / scoped 类 / 扫描包；SDK 不内置 kit，
 * 参考 cn.geelato.script.kit.demo.DemoService）、profile、revision、数据源与出网配置、
 * ORM/身份 SPI。build() 时统一 fail-fast 校验并装配缺省项（default profile、v1 revision）。
 */
public class ScriptEngineBuilder {

    private final List<Object> kitInstances = new ArrayList<>();
    private final List<Class<?>> kitClasses = new ArrayList<>();
    private final List<String> scanPackages = new ArrayList<>();
    private final Map<String, String> hostPackages = new LinkedHashMap<>();
    private List<String> hostExcludes = new ArrayList<>();
    private final Map<String, ScopedRegistration> scopedFactories = new LinkedHashMap<>();
    private final Map<String, ScriptProfile> profiles = new LinkedHashMap<>();
    private final Map<String, EngineRevision> revisions = new LinkedHashMap<>();
    private final List<DeriveSpec> deriveSpecs = new ArrayList<>();
    private final List<ScriptLanguageExecutor> languageExecutors = new ArrayList<>();

    private long statementLimit = 1_000_000L;
    private int workerPoolSize = 4;
    private boolean engineShared = true;
    private boolean pythonEnabled = true;
    private String defaultVersion = "v1";
    private String defaultEsVersion = "2022";
    private DataSourceSupplier dataSourceSupplier;
    private int sqlRowLimit = 10_000;
    private int sqlQueryTimeoutMs = 10_000;
    private boolean httpEnabled = true;
    private List<String> httpAllowlist = new ArrayList<>();
    private int httpTimeoutMs = 5_000;
    private DaoSupplier daoSupplier;
    private OrmExecutionWrapper ormWrapper;
    private cn.geelato.script.kit.sql.ConnectPoolProvider connectPoolProvider;
    private IdentitySupplier identitySupplier = IdentitySnapshot::captureCurrent;
    private String aesKey;
    private boolean daoWriteEnabled = true;
    private boolean daoDryRun = true;

    public ScriptEngineBuilder registerKit(Object instance) {
        kitInstances.add(instance);
        return this;
    }

    public ScriptEngineBuilder registerKit(Class<?> clazz) {
        kitClasses.add(clazz);
        return this;
    }

    public ScriptEngineBuilder registerScoped(String name, Class<?> kitClass, cn.geelato.script.kit.ScopedKitFactory factory) {
        scopedFactories.put(name, new ScopedRegistration(name, kitClass, factory));
        return this;
    }

    public ScriptEngineBuilder scanPackage(String pkg) {
        if (pkg != null && !pkg.isBlank()) {
            scanPackages.add(pkg.trim());
        }
        return this;
    }

    /** 整库反射注入：库别名 + 根包（合并挂载为顶层 $kit.{别名}.{类简单名小写}.{方法}）。 */
    public ScriptEngineBuilder hostPackage(String alias, String packageName) {
        if (alias != null && !alias.isBlank() && packageName != null && !packageName.isBlank()) {
            hostPackages.put(alias.trim(), packageName.trim());
        }
        return this;
    }

    public ScriptEngineBuilder hostExcludes(List<String> excludes) {
        this.hostExcludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        return this;
    }

    public ScriptEngineBuilder profile(ScriptProfile profile) {
        profiles.put(profile.getId(), profile);
        return this;
    }

    public ScriptEngineBuilder revision(EngineRevision revision) {
        revisions.put(revision.getId(), revision);
        return this;
    }

    /** 构建期派生版本：以 baselineId（空=缺省版本）快照为底应用差异声明，build() 时注册。 */
    public ScriptEngineBuilder deriveRevision(String newId, String baselineId,
                                              java.util.function.Consumer<RevisionDeriver> mutator) {
        deriveSpecs.add(new DeriveSpec(newId, baselineId, mutator));
        return this;
    }

    public ScriptEngineBuilder languageExecutor(ScriptLanguageExecutor executor) {
        languageExecutors.add(executor);
        return this;
    }

    public ScriptEngineBuilder statementLimit(long statementLimit) {
        this.statementLimit = statementLimit;
        return this;
    }

    public ScriptEngineBuilder workerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
        return this;
    }

    public ScriptEngineBuilder engineShared(boolean engineShared) {
        this.engineShared = engineShared;
        return this;
    }

    public ScriptEngineBuilder pythonEnabled(boolean pythonEnabled) {
        this.pythonEnabled = pythonEnabled;
        return this;
    }

    public ScriptEngineBuilder defaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
        return this;
    }

    public ScriptEngineBuilder defaultEsVersion(String defaultEsVersion) {
        this.defaultEsVersion = defaultEsVersion;
        return this;
    }

    public ScriptEngineBuilder dataSourceSupplier(DataSourceSupplier supplier) {
        this.dataSourceSupplier = supplier;
        return this;
    }

    public ScriptEngineBuilder sqlRowLimit(int sqlRowLimit) {
        this.sqlRowLimit = sqlRowLimit;
        return this;
    }

    public ScriptEngineBuilder sqlQueryTimeoutMs(int sqlQueryTimeoutMs) {
        this.sqlQueryTimeoutMs = sqlQueryTimeoutMs;
        return this;
    }

    public ScriptEngineBuilder httpEnabled(boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
        return this;
    }

    public ScriptEngineBuilder httpAllowlist(List<String> allowlist) {
        this.httpAllowlist = allowlist == null ? new ArrayList<>() : new ArrayList<>(allowlist);
        return this;
    }

    public ScriptEngineBuilder httpTimeoutMs(int httpTimeoutMs) {
        this.httpTimeoutMs = httpTimeoutMs;
        return this;
    }

    /** 平台 ORM Dao 供给（ORM/MQL 通道启用时必须）。 */
    public ScriptEngineBuilder daoSupplier(DaoSupplier daoSupplier) {
        this.daoSupplier = daoSupplier;
        return this;
    }

    /** 联邦查询按 connectId 取池（null 走 core DataSourceManager 缺省；带动态连接的宿主应注入 orm Registry 支撑实现）。 */
    public ScriptEngineBuilder connectPoolProvider(cn.geelato.script.kit.sql.ConnectPoolProvider provider) {
        this.connectPoolProvider = provider;
        return this;
    }

    /** ORM 调用包裹（connectId 路由 + 线程上下文重建；缺省直跑）。 */
    public ScriptEngineBuilder ormWrapper(OrmExecutionWrapper ormWrapper) {
        this.ormWrapper = ormWrapper;
        return this;
    }

    /** 身份快照来源（缺省调用线程 SecurityContext 捕获）。 */
    public ScriptEngineBuilder identitySupplier(IdentitySupplier identitySupplier) {
        this.identitySupplier = identitySupplier;
        return this;
    }

    /** AES 密钥（$gl.encrypt.aes/aesDecrypt；未配置时两方法抛错）。 */
    public ScriptEngineBuilder encryptKey(String aesKey) {
        this.aesKey = aesKey;
        return this;
    }

    /** 写通道总开关（$gl.sql 写方法与 $gl.mql save/delete）。 */
    public ScriptEngineBuilder daoWriteEnabled(boolean daoWriteEnabled) {
        this.daoWriteEnabled = daoWriteEnabled;
        return this;
    }

    /** 写通道缺省 dry-run（true=事务内执行后回滚；ScriptExecution.dryRun 可执行级覆盖）。 */
    public ScriptEngineBuilder daoDryRun(boolean daoDryRun) {
        this.daoDryRun = daoDryRun;
        return this;
    }

    public DefaultScriptEngine build() {
        KitRegistry registry = new KitRegistry();
        for (Object instance : kitInstances) {
            registry.register(instance);
        }
        for (Class<?> clazz : kitClasses) {
            registry.register(clazz);
        }
        for (ScopedRegistration reg : scopedFactories.values()) {
            registry.registerScoped(reg.name, reg.kitClass, reg.factory);
        }
        registry.scanPackages(scanPackages);
        registry.scanHostPackages(hostPackages, hostExcludes);
        registerBuiltins(registry);
        registry.validate();

        Map<String, ScriptProfile> allProfiles = new LinkedHashMap<>(profiles);
        allProfiles.putIfAbsent("default", ScriptProfile.builder()
                .id("default")
                .namespaces(java.util.Set.of(ScriptProfile.ALL))
                .variables(java.util.Set.of(ScriptProfile.ALL))
                .frozenBindings(false)
                .defaultTimeoutMs(30_000)
                .build());

        Map<String, EngineRevision> allRevisions = new LinkedHashMap<>(revisions);
        if (!allRevisions.containsKey(defaultVersion)) {
            allRevisions.put(defaultVersion, EngineRevision.snapshotOf(defaultVersion, EngineRevision.Status.CURRENT,
                    null, defaultEsVersion, registry));
        } else {
            allRevisions.putIfAbsent("v1", EngineRevision.snapshotOf("v1", EngineRevision.Status.CURRENT,
                    null, defaultEsVersion, registry));
        }
        for (DeriveSpec spec : deriveSpecs) {
            String baselineId = spec.baselineId() == null || spec.baselineId().isBlank()
                    ? defaultVersion : spec.baselineId();
            EngineRevision baseline = allRevisions.get(baselineId);
            if (baseline == null) {
                throw new IllegalStateException("派生基线版本不存在: " + baselineId + "（可用: " + allRevisions.keySet() + "）");
            }
            RevisionDeriver deriver = new RevisionDeriver(spec.newId(), baseline);
            spec.mutator().accept(deriver);
            EngineRevision derived = deriver.build();
            if (allRevisions.containsKey(derived.getId())) {
                throw new IllegalStateException("派生版本 id 已存在: " + derived.getId());
            }
            allRevisions.put(derived.getId(), derived);
        }

        List<ScriptLanguageExecutor> executors = new ArrayList<>(languageExecutors);
        executors.removeIf(e -> "python".equals(e.language()) && !pythonEnabled);
        executors.add(new JsLanguageExecutor());

        DefaultScriptEngine.EngineOptions options = new DefaultScriptEngine.EngineOptions(
                statementLimit, workerPoolSize,
                dataSourceSupplier, sqlRowLimit, sqlQueryTimeoutMs,
                httpEnabled, httpAllowlist, httpTimeoutMs,
                daoSupplier, ormWrapper, identitySupplier, aesKey,
                daoWriteEnabled, daoDryRun, executors, connectPoolProvider);
        return new DefaultScriptEngine(options, allRevisions, defaultVersion, allProfiles,
                new SandboxContextFactory(engineShared));
    }

    /** 内置 kit 默认注册：消费方显式注册（registerKit/registerScoped/scanPackages）同名者优先，跳过默认。 */
    private static void registerBuiltins(KitRegistry registry) {
        List<Class<?>> builtins = List.of(
                cn.geelato.script.kit.builtin.SqlKit.class,
                cn.geelato.script.kit.builtin.MqlKit.class,
                cn.geelato.script.kit.builtin.HttpKit.class,
                cn.geelato.script.kit.builtin.EncryptKit.class,
                cn.geelato.script.kit.builtin.CtxKit.class,
                cn.geelato.script.kit.builtin.ParameterKit.class);
        for (Class<?> kit : builtins) {
            cn.geelato.core.graal.GraalService anno = kit.getAnnotation(cn.geelato.core.graal.GraalService.class);
            if (anno != null && !registry.getDefinitions().containsKey(anno.name())) {
                registry.register(kit);
            }
        }
    }

    private record ScopedRegistration(String name, Class<?> kitClass,
                                       cn.geelato.script.kit.ScopedKitFactory factory) {
    }

    private record DeriveSpec(String newId, String baselineId,
                              java.util.function.Consumer<RevisionDeriver> mutator) {
    }
}
