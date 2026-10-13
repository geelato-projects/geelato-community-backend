package cn.geelato.script.core;

import cn.geelato.script.kit.KitDefinition;
import cn.geelato.script.kit.KitRegistry;
import cn.geelato.script.kit.ScopedKitFactory;
import cn.geelato.script.kit.ScopedKitSlot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 版本派生差异声明：以基线 revision 快照为底，声明 kit 覆盖/增删与语言级别差异后 build 出新版本。
 * 新增/覆盖的 kit 经 interop 白名单 fail-fast 校验。经 ScriptEngineBuilder.deriveRevision（构建期）
 * 或 EngineAdmin.deriveRevision（运行时）取得。
 */
public final class RevisionDeriver {

    private final String id;
    private final Map<String, Object> singletonKits = new LinkedHashMap<>();
    private final Map<String, ScopedKitSlot> scopedKits = new LinkedHashMap<>();
    private final Map<String, KitDefinition> definitions = new LinkedHashMap<>();
    private String esVersion;
    private EngineRevision.Status status = EngineRevision.Status.CURRENT;
    private String migrateHint;

    RevisionDeriver(String id, EngineRevision baseline) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("派生版本 id 为空");
        }
        this.id = id;
        this.esVersion = baseline.getEsVersion();
        this.singletonKits.putAll(baseline.getSingletonKits());
        this.scopedKits.putAll(baseline.getScopedKits());
        this.definitions.putAll(baseline.getDefinitions());
    }

    /** js.ecmascript-version（继承基线后覆盖；null 用引擎缺省）。 */
    public RevisionDeriver esVersion(String esVersion) {
        this.esVersion = esVersion;
        return this;
    }

    /** 覆盖/新增无状态 kit（ns 须与 @GraalService name 一致）。 */
    public RevisionDeriver singleton(String ns, Object kit) {
        KitDefinition def = new KitDefinition(kit, null);
        requireNameMatch(ns, def);
        definitions.put(ns, def);
        singletonKits.put(ns, kit);
        scopedKits.remove(ns);
        return this;
    }

    /** 覆盖/新增 scoped kit：每执行实例化（构造器为 (KitContext) 或无参）。 */
    public RevisionDeriver scoped(String ns, Class<?> kitClass) {
        return scoped(ns, kitClass, null);
    }

    /** 覆盖/新增 scoped kit（带工厂，kit 持外部依赖时用）。 */
    public RevisionDeriver scoped(String ns, Class<?> kitClass, ScopedKitFactory factory) {
        KitDefinition def = new KitDefinition(null, kitClass);
        requireNameMatch(ns, def);
        definitions.put(ns, def);
        scopedKits.put(ns, new ScopedKitSlot(kitClass, factory));
        singletonKits.remove(ns);
        return this;
    }

    public RevisionDeriver remove(String ns) {
        definitions.remove(ns);
        singletonKits.remove(ns);
        scopedKits.remove(ns);
        return this;
    }

    /** 新版本注册时的初始状态（常规升级无需设置，缺省 CURRENT）。 */
    public RevisionDeriver status(EngineRevision.Status status, String migrateHint) {
        this.status = status;
        this.migrateHint = migrateHint;
        return this;
    }

    public EngineRevision build() {
        KitRegistry.validate(definitions.values());
        return new EngineRevision(id, status, migrateHint, esVersion, singletonKits, scopedKits, definitions);
    }

    private static void requireNameMatch(String ns, KitDefinition def) {
        if (!ns.equals(def.getName())) {
            throw new IllegalStateException("版本化 kit 注册名不一致: 注册名=" + ns + " 注解名=" + def.getName());
        }
    }
}
