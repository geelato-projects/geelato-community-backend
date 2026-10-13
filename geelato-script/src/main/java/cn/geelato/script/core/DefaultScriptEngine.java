package cn.geelato.script.core;

import cn.geelato.script.kit.KitContext;
import cn.geelato.script.kit.KitDefinition;
import cn.geelato.script.kit.DaoSupplier;
import cn.geelato.script.kit.DataSourceSupplier;
import cn.geelato.script.kit.KitRegistry;
import cn.geelato.script.kit.IdentitySupplier;
import cn.geelato.script.kit.OrmExecutionWrapper;
import cn.geelato.script.lang.JsLanguageExecutor;
import cn.geelato.script.lang.ScriptLanguageExecutor;
import cn.geelato.script.sandbox.ConsoleCollector;
import cn.geelato.script.sandbox.SandboxContextFactory;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 引擎实现：每次执行独立 Context 故障隔离，worker 池 + Future 超时 + close(true) 强杀；
 * kit 按 revision 快照实例化（不回退全局注册表），按 profile 过滤可见命名空间与可注入变量；
 * $gl 为 guest 侧对象（built=true kit 挂其下），built=false kit 以 $+name 挂顶层变量。
 * 每执行生成 execId、解析身份快照（调用线程）、组装 $env、采集 kit 调用 trace。
 * 版本清单/缺省版本为运行时可变状态（EngineAdmin 写、执行路径读，volatile/并发容器）。
 */
@Slf4j
public class DefaultScriptEngine implements ScriptEngine, EngineAdmin {

    private static final int MAX_LOG_TEXT = 2000;

    private final EngineOptions options;
    private final Map<String, EngineRevision> revisions;
    private volatile String defaultVersion;
    private final Map<String, ScriptProfile> profiles;
    private final SandboxContextFactory contextFactory;
    private final Map<String, ScriptLanguageExecutor> languageExecutors;
    private final ExecutorService workers;

    DefaultScriptEngine(EngineOptions options, Map<String, EngineRevision> revisions, String defaultVersion,
                        Map<String, ScriptProfile> profiles,
                        SandboxContextFactory contextFactory) {
        this.options = options;
        this.revisions = new java.util.concurrent.ConcurrentHashMap<>(revisions);
        this.defaultVersion = defaultVersion;
        this.profiles = profiles;
        this.contextFactory = contextFactory;
        this.languageExecutors = new LinkedHashMap<>();
        for (ScriptLanguageExecutor executor : options.languageExecutors()) {
            this.languageExecutors.put(executor.language(), executor);
        }
        this.workers = Executors.newFixedThreadPool(Math.max(1, options.workerPoolSize()), r -> {
            Thread t = new Thread(r, "graal-script-sdk");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ScriptResult execute(ScriptExecution execution) {
        String execId = newExecId();
        EngineRevision revision = resolveRevision(execution.getEngineVersion());
        if (revision.getStatus() == EngineRevision.Status.RETIRED) {
            throw new IllegalArgumentException("引擎版本已下线: " + revision.getId()
                    + (revision.getMigrateHint() == null ? "" : "（" + revision.getMigrateHint() + "）"));
        }
        ScriptProfile profile = resolveProfile(execution.getProfileId());
        ScriptLanguageExecutor langExecutor = resolveLanguage(execution.getLang());
        long timeout = execution.getTimeoutMs() != null ? execution.getTimeoutMs() : profile.getDefaultTimeoutMs();
        IdentitySnapshot identity = execution.getIdentity() != null ? execution.getIdentity()
                : (options.identitySupplier() != null ? options.identitySupplier().get() : IdentitySnapshot.empty());
        Map<String, Object> bindings = effectiveBindings(execution);
        boolean dryRun = execution.getDryRun() != null ? execution.getDryRun() : options.daoDryRun();
        KitContext kitCtx = newKitContext(execution.getAttributes(), execId, identity, bindings, dryRun);
        logStart(execId, execution, revision, profile, langExecutor, timeout, dryRun, identity, bindings);

        long start = System.currentTimeMillis();
        ConsoleCollector console = new ConsoleCollector();
        try (Context context = contextFactory.create(new String[]{langExecutor.language()},
                profile.getStatementLimit() != null ? profile.getStatementLimit() : options.statementLimit(),
                revision.getEsVersion())) {
            console.inject(context);
            injectKits(context, revision, profile, langExecutor.language(), kitCtx);
            injectVariables(langExecutor, context, bindings, profile);
            injectEnv(langExecutor, context, buildEnv(execId, langExecutor, revision, profile, timeout, identity, execution));
            Object value = runWithTimeout(context, langExecutor,
                    execution.getContent() == null ? "" : execution.getContent(), "script", timeout, console);
            long cost = System.currentTimeMillis() - start;
            ScriptResult result = ScriptResult.success(value, console.getLogs(), cost, revision.getId());
            result.setExecId(execId);
            result.setRowCount(kitCtx.getRowCount().get());
            result.setTrace(kitCtx.getTraceSnapshot());
            applyDeprecation(result, revision, console);
            log.info("SCRIPT-DONE exec={} status=SUCCESS cost={}ms rows={} trace={} console={} value={}",
                    execId, cost, result.getRowCount(), result.getTrace().size(),
                    console.getLogs().size(), toJson(value));
            return result;
        } catch (EngineTimeoutException e) {
            long cost = System.currentTimeMillis() - start;
            ScriptResult r = ScriptResult.failure("TIMEOUT", ScriptError.of("TIMEOUT", "执行超时（" + timeout + "ms）已强制终止"),
                    console.getLogs(), cost, revision.getId());
            r.setExecId(execId);
            r.setRowCount(kitCtx.getRowCount().get());
            r.setTrace(kitCtx.getTraceSnapshot());
            applyDeprecation(r, revision, console);
            logFail(execId, "TIMEOUT", r.getError(), cost, console);
            return r;
        } catch (ScriptFailureException e) {
            long cost = System.currentTimeMillis() - start;
            ScriptResult r = ScriptResult.failure("ERROR", e.getError(), console.getLogs(), cost, revision.getId());
            r.setExecId(execId);
            r.setRowCount(kitCtx.getRowCount().get());
            r.setTrace(kitCtx.getTraceSnapshot());
            applyDeprecation(r, revision, console);
            logFail(execId, "ERROR", e.getError(), cost, console);
            return r;
        }
    }

    @Override
    public List<ScriptIssue> parseOnly(String lang, String content, String engineVersion) {
        EngineRevision revision = resolveRevision(engineVersion);
        ScriptLanguageExecutor langExecutor = resolveLanguage(lang);
        try (Context context = contextFactory.create(new String[]{langExecutor.language()}, options.statementLimit(), revision.getEsVersion())) {
            return langExecutor.parseOnly(context, content, "parse");
        }
    }

    @Override
    public List<cn.geelato.core.graal.GraalServiceDescription> kitCatalog(String profileId, String engineVersion) {
        ScriptProfile profile = resolveProfile(profileId);
        EngineRevision revision = resolveRevision(engineVersion);
        return KitRegistry.catalog(revision.getDefinitions().values(), profile::namespaceAllowed);
    }

    @Override
    public List<EngineVersionInfo> versions() {
        List<EngineVersionInfo> list = new ArrayList<>();
        for (EngineRevision revision : revisions.values()) {
            list.add(versionInfo(revision));
        }
        return list;
    }

    @Override
    public EngineVersionInfo setDefault(String versionId) {
        EngineRevision revision = resolveRevision(versionId);
        if (revision.getStatus() == EngineRevision.Status.RETIRED) {
            throw new IllegalArgumentException("已下线版本不可设为缺省: " + revision.getId());
        }
        this.defaultVersion = revision.getId();
        return versionInfo(revision);
    }

    @Override
    public EngineVersionInfo deprecate(String versionId, String migrateHint) {
        EngineRevision revision = resolveRevision(versionId);
        if (revision.getStatus() == EngineRevision.Status.RETIRED) {
            throw new IllegalArgumentException("已下线版本不可再标记弃用: " + revision.getId());
        }
        if (revision.getStatus() == EngineRevision.Status.CURRENT && countCurrent() <= 1) {
            throw new IllegalArgumentException("至少须保留一个 CURRENT 版本，不可弃用: " + revision.getId());
        }
        revision.markDeprecated(migrateHint);
        return versionInfo(revision);
    }

    @Override
    public EngineVersionInfo retire(String versionId) {
        EngineRevision revision = resolveRevision(versionId);
        if (revision.getStatus() != EngineRevision.Status.DEPRECATED) {
            throw new IllegalArgumentException("下线前须先标记弃用: " + revision.getId());
        }
        revision.markRetired();
        return versionInfo(revision);
    }

    @Override
    public EngineVersionInfo registerRevision(EngineRevision revision) {
        if (revision == null || revision.getId() == null || revision.getId().isBlank()) {
            throw new IllegalArgumentException("revision 或其 id 为空");
        }
        if (revisions.putIfAbsent(revision.getId(), revision) != null) {
            throw new IllegalArgumentException("引擎版本已存在: " + revision.getId());
        }
        return versionInfo(revision);
    }

    @Override
    public EngineRevision deriveRevision(String id, String baselineId, java.util.function.Consumer<RevisionDeriver> mutator) {
        EngineRevision baseline = resolveRevision(baselineId);
        RevisionDeriver deriver = new RevisionDeriver(id, baseline);
        mutator.accept(deriver);
        return deriver.build();
    }

    private static EngineVersionInfo versionInfo(EngineRevision revision) {
        EngineVersionInfo info = new EngineVersionInfo();
        info.setId(revision.getId());
        info.setStatus(revision.getStatus().name());
        info.setMigrateHint(revision.getMigrateHint());
        List<String> namespaces = new ArrayList<>();
        namespaces.addAll(revision.getSingletonKits().keySet());
        namespaces.addAll(revision.getScopedKits().keySet());
        info.setNamespaces(namespaces);
        return info;
    }

    private long countCurrent() {
        return revisions.values().stream()
                .filter(r -> r.getStatus() == EngineRevision.Status.CURRENT).count();
    }

    public void close() {
        workers.shutdownNow();
    }

    // ==================== 内部 ====================

    private KitContext newKitContext() {
        return KitContext.builder().build();
    }

    private KitContext newKitContext(Map<String, Object> attributes) {
        return KitContext.builder().attributes(attributes).build();
    }

    private KitContext newKitContext(Map<String, Object> attributes, String execId, IdentitySnapshot identity,
                                     Map<String, Object> bindings, boolean dryRun) {
        return KitContext.builder()
                .dataSourceSupplier(options.dataSourceSupplier())
                .sqlRowLimit(options.sqlRowLimit())
                .sqlQueryTimeoutMs(options.sqlQueryTimeoutMs())
                .httpEnabled(options.httpEnabled())
                .httpAllowlist(options.httpAllowlist())
                .httpTimeoutMs(options.httpTimeoutMs())
                .attributes(attributes)
                .execId(execId)
                .identity(identity)
                .daoSupplier(options.daoSupplier())
                .ormWrapper(options.ormWrapper())
                .aesKey(options.aesKey())
                .daoWriteEnabled(options.daoWriteEnabled())
                .daoDryRun(dryRun)
                .bindings(bindings)
                .build();
    }

    private static String newExecId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static Map<String, Object> effectiveBindings(ScriptExecution execution) {
        Map<String, Object> bindings = execution.getBindings();
        if (execution.getParams() == null) {
            return bindings;
        }
        if (bindings != null && bindings.containsKey("$params")) {
            return bindings;
        }
        Map<String, Object> merged = new LinkedHashMap<>(bindings == null ? Map.of() : bindings);
        merged.put("$params", execution.getParams());
        return merged;
    }

    private Map<String, Object> buildEnv(String execId, ScriptLanguageExecutor langExecutor, EngineRevision revision,
                                         ScriptProfile profile, long timeout, IdentitySnapshot identity,
                                         ScriptExecution execution) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("execId", execId);
        env.put("lang", langExecutor.language());
        env.put("engineVersion", revision.getId());
        env.put("profileId", profile.getId());
        env.put("startedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        env.put("timeoutMs", timeout);
        if (identity != null) {
            env.put("tenantCode", identity.getTenant() == null ? null : identity.getTenant().getCode());
            env.put("tenantName", identity.getTenant() == null ? null : identity.getTenant().getName());
            env.put("appId", identity.getApp() == null ? null : identity.getApp().getId());
            env.put("appName", identity.getApp() == null ? null : identity.getApp().getName());
            Map<String, Object> user = null;
            if (identity.getUser() != null) {
                user = new LinkedHashMap<>();
                user.put("id", identity.getUser().getUserId());
                user.put("userId", identity.getUser().getUserId());
                user.put("userName", identity.getUser().getUserName());
                user.put("loginName", identity.getUser().getLoginName());
                user.put("orgId", identity.getUser().getOrgId());
                user.put("orgName", identity.getUser().getOrgName());
                user.put("deptId", identity.getUser().getDeptId());
            }
            env.put("user", user);
        }
        if (execution.getAttributes() != null) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : execution.getAttributes().entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith("env.")) {
                    attrs.put(e.getKey().substring("env.".length()), e.getValue());
                }
            }
            if (!attrs.isEmpty()) {
                env.put("attrs", attrs);
            }
        }
        return env;
    }

    private void injectEnv(ScriptLanguageExecutor langExecutor, Context context, Map<String, Object> env) {
        langExecutor.bindVariables(context, Map.of("$env", env), true);
    }

    private EngineRevision resolveRevision(String version) {
        String id = version == null || version.isBlank() ? defaultVersion : version;
        EngineRevision revision = revisions.get(id);
        if (revision == null) {
            throw new IllegalArgumentException("未知引擎版本: " + id + "（可用: " + revisions.keySet() + "）");
        }
        return revision;
    }

    private ScriptProfile resolveProfile(String profileId) {
        String id = profileId == null || profileId.isBlank() ? "default" : profileId;
        ScriptProfile profile = profiles.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("未知 ScriptProfile: " + id + "（可用: " + profiles.keySet() + "）");
        }
        return profile;
    }

    private ScriptLanguageExecutor resolveLanguage(String lang) {
        String id = lang == null || lang.isBlank() ? "js" : lang;
        ScriptLanguageExecutor executor = languageExecutors.get(id);
        if (executor == null) {
            throw new IllegalArgumentException("不支持的脚本语言: " + id + "（可用: " + languageExecutors.keySet() + "）");
        }
        return executor;
    }

    private void injectKits(Context context, EngineRevision revision, ScriptProfile profile, String language,
                               KitContext kitCtx) {
        Map<String, Object> active = new LinkedHashMap<>(revision.getSingletonKits());
        for (Map.Entry<String, cn.geelato.script.kit.ScopedKitSlot> e : revision.getScopedKits().entrySet()) {
            if (profile.namespaceAllowed(e.getKey())) {
                active.put(e.getKey(), newScopedInstance(e.getValue(), kitCtx));
            }
        }
        Map<String, Object> gl = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : active.entrySet()) {
            if (!profile.namespaceAllowed(e.getKey())) {
                continue;
            }
            // @GraalFunction kit 经 guest 侧适配（arity 宽松 + 反射调用）；属性式 ProxyObject kit 原样挂载
            Object expose = cn.geelato.script.lang.KitAdapter.adaptable(e.getValue())
                    ? cn.geelato.script.lang.KitAdapter.of(e.getValue()) : e.getValue();
            KitDefinition def = revision.getDefinitions().get(e.getKey());
            if (def != null && def.isBuilt()) {
                gl.put(e.getKey(), expose);
            } else {
                context.getBindings(language).putMember(def != null ? def.exposeName() : e.getKey(), expose);
            }
        }
        gl.putIfAbsent("help", (org.graalvm.polyglot.proxy.ProxyExecutable) args ->
                cn.geelato.script.lang.KitHelpFormatter.format(revision.getDefinitions().values(),
                        profile::namespaceAllowed, helpNamespace(args)));
        context.getBindings(language).putMember("$gl", cn.geelato.script.lang.GuestValues.toGuest(gl));
    }

    private static String helpNamespace(org.graalvm.polyglot.Value[] args) {
        if (args == null || args.length == 0 || !args[0].isString()) {
            return null;
        }
        return args[0].asString();
    }

    private Object newScopedInstance(cn.geelato.script.kit.ScopedKitSlot slot, KitContext kitCtx) {
        if (slot.factory() != null) {
            return slot.factory().create(kitCtx);
        }
        Class<?> clazz = slot.kitClass();
        try {
            for (java.lang.reflect.Constructor<?> c : clazz.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 1 && params[0] == KitContext.class) {
                    return c.newInstance(kitCtx);
                }
            }
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("scoped kit 实例化失败: " + clazz.getName(), e);
        }
    }

    private void injectVariables(ScriptLanguageExecutor langExecutor, Context context,
                                 Map<String, Object> bindings, ScriptProfile profile) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (bindings != null) {
            for (Map.Entry<String, Object> e : bindings.entrySet()) {
                if (profile.variableAllowed(e.getKey())) {
                    vars.put(e.getKey(), e.getValue());
                }
            }
        }
        langExecutor.bindVariables(context, vars, profile.isFrozenBindings());
    }

    private Object runWithTimeout(Context context, ScriptLanguageExecutor langExecutor,
                                  String content, String stepName, long timeoutMs, ConsoleCollector console) {
        Future<Object> future = workers.submit(() -> langExecutor.execute(context, content, stepName));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            context.close(true);
            future.cancel(true);
            throw new EngineTimeoutException();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ScriptFailureException(toError(cause, langExecutor));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.close(true);
            future.cancel(true);
            throw new ScriptFailureException(ScriptError.of("UNKNOWN", "执行被中断"));
        }
    }

    private ScriptError toError(Throwable t, ScriptLanguageExecutor langExecutor) {
        int offset = langExecutor instanceof JsLanguageExecutor ? JsLanguageExecutor.IIFE_LINE_OFFSET : 0;
        if (t instanceof PolyglotException pe) {
            ScriptError error = ScriptError.of(pe.isSyntaxError() ? "SYNTAX" : "RUNTIME", pe.getMessage());
            if (pe.getSourceLocation() != null) {
                error.setLine(Math.max(1, pe.getSourceLocation().getStartLine() - offset));
                error.setCol(pe.getSourceLocation().getStartColumn());
            }
            error.setStack(polyglotStack(pe, offset));
            return error;
        }
        return ScriptError.of("RUNTIME", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
    }

    /** polyglot 调用栈（≤50 帧，行号为用户脚本行号）。 */
    private static List<String> polyglotStack(PolyglotException pe, int offset) {
        List<String> out = new ArrayList<>();
        try {
            Iterable<PolyglotException.StackFrame> frames = pe.getPolyglotStackTrace();
            if (frames == null) {
                return out;
            }
            int count = 0;
            for (PolyglotException.StackFrame f : frames) {
                if (count++ >= 50) {
                    out.add("... (more frames truncated)");
                    break;
                }
                StringBuilder sb = new StringBuilder("  at ");
                String name = null;
                try {
                    name = f.getRootName();
                } catch (Exception ignored) {
                }
                sb.append(name == null || name.isEmpty() ? "<anonymous>" : name);
                try {
                    var sec = f.getSourceLocation();
                    if (sec != null) {
                        sb.append(" (");
                        String srcName = sec.getSource() != null ? sec.getSource().getName() : "unknown";
                        sb.append(srcName);
                        if (sec.hasLines()) {
                            sb.append(":").append(Math.max(1, sec.getStartLine() - offset));
                        }
                        sb.append(")");
                    }
                } catch (Exception ignored) {
                }
                out.add(sb.toString());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void applyDeprecation(ScriptResult result, EngineRevision revision, ConsoleCollector console) {
        if (revision.getStatus() == EngineRevision.Status.DEPRECATED) {
            result.setDeprecationWarning(deprecation(revision));
            console.getLogs().add(0, "[warn] 引擎版本 " + revision.getId() + " 已标记弃用，请尽快迁移");
        }
    }

    private static String deprecation(EngineRevision revision) {
        return "引擎版本 " + revision.getId() + " 已弃用"
                + (revision.getMigrateHint() == null ? "" : "：" + revision.getMigrateHint());
    }

    // ==================== 执行日志 ====================

    private void logStart(String execId, ScriptExecution execution, EngineRevision revision, ScriptProfile profile,
                          ScriptLanguageExecutor langExecutor, long timeout, boolean dryRun,
                          IdentitySnapshot identity, Map<String, Object> bindings) {
        String name = execution.getScriptName() == null || execution.getScriptName().isBlank()
                ? "-" : execution.getScriptName();
        String tenant = identity != null && identity.getTenant() != null ? identity.getTenant().getCode() : "-";
        String user = identity != null && identity.getUser() != null ? identity.getUser().getUserName() : "-";
        log.info("SCRIPT-START exec={} name='{}' lang={} profile={} version={} dryRun={} timeout={}ms tenant={} user={}",
                execId, name, langExecutor.language(), profile.getId(), revision.getId(), dryRun, timeout, tenant, user);
        String content = execution.getContent() == null ? "" : execution.getContent();
        log.info("SCRIPT-BODY exec={} len={} :: {}", execId, content.length(), abbreviate(content));
        log.info("SCRIPT-PARAM exec={} params={} bindings={}", execId, toJson(execution.getParams()), toJson(bindings));
    }

    private void logFail(String execId, String status, ScriptError error, long costMs, ConsoleCollector console) {
        log.error("SCRIPT-FAIL exec={} status={} type={} line={} col={} cost={}ms msg={}",
                execId, status, error.getType(), error.getLine(), error.getCol(), costMs,
                error.getMessage() == null ? "-" : error.getMessage());
        if (error.getStack() != null) {
            for (String frame : error.getStack()) {
                log.error("SCRIPT-FAIL exec={} at {}", execId, frame);
            }
        }
        List<String> logs = console.getLogs();
        for (int i = Math.max(0, logs.size() - 10); i < logs.size(); i++) {
            log.error("CONSOLE exec={} {}", execId, abbreviate(logs.get(i)));
        }
    }

    /** 日志单行化：换行转义 + 超长截断（与 ConsoleCollector 单条上限同口径）。 */
    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
        return one.length() <= MAX_LOG_TEXT ? one
                : one.substring(0, MAX_LOG_TEXT) + "...(已截断,总长" + one.length() + ")";
    }

    private static String toJson(Object v) {
        if (v == null) {
            return "{}";
        }
        try {
            return abbreviate(com.alibaba.fastjson2.JSON.toJSONString(v));
        } catch (Exception e) {
            return abbreviate(String.valueOf(v));
        }
    }

    /** 引擎聚合配置（Builder 组装；SPI 引用可空，空值走各通道的缺省/报错语义）。 */
    public record EngineOptions(long statementLimit, int workerPoolSize,
                                DataSourceSupplier dataSourceSupplier, int sqlRowLimit, int sqlQueryTimeoutMs,
                                boolean httpEnabled, List<String> httpAllowlist, int httpTimeoutMs,
                                DaoSupplier daoSupplier, OrmExecutionWrapper ormWrapper,
                                IdentitySupplier identitySupplier, String aesKey,
                                boolean daoWriteEnabled, boolean daoDryRun,
                                List<ScriptLanguageExecutor> languageExecutors) {
    }

    private static final class EngineTimeoutException extends RuntimeException {
    }

    private static final class ScriptFailureException extends RuntimeException {
        private final ScriptError error;

        ScriptFailureException(ScriptError error) {
            super(error.getMessage());
            this.error = error;
        }

        ScriptError getError() {
            return error;
        }
    }
}
