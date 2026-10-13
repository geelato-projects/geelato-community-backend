package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.core.graal.GraalServiceDescription;
import cn.geelato.utils.AnnotatedClassScanner;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注解 kit 注册表（实例级，不挂 GraalManager 全局单例）：
 * 注册通道 —— register(实例)（Spring Bean 优先）/ register(类) / scanPackages(类路径扫描 newInstance 兜底）；
 * 注册完成即 fail-fast（命名冲突、方法参数/返回类型 interop 兼容白名单）。
 */
public class KitRegistry {

    private static final Set<Class<?>> PARAM_TYPES = Set.of(
            String.class, Object.class, Map.class, List.class, Value.class, Date.class, Boolean.class);
    private static final Set<Class<?>> RETURN_TYPES = Set.of(
            String.class, Object.class, Map.class, List.class, Value.class, Date.class, Boolean.class, void.class, Void.class);

    private final Map<String, KitDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ScopedKitFactory> scopedFactories = new LinkedHashMap<>();
    private final Map<String, Class<?>> scopedFactoryClasses = new LinkedHashMap<>();
    /** 经 registerScoped 工厂通道注册的命名空间：scoped 与否由注册通道决定，构造器形状仅作类注册的隐式推断。 */
    private final Set<String> factoryScopedNames = new LinkedHashSet<>();

    public void register(Object instance) {
        KitDefinition def = new KitDefinition(instance, null);
        put(def, instance.getClass().getName());
    }

    public void register(Class<?> clazz) {
        KitDefinition def = new KitDefinition(null, clazz);
        put(def, clazz.getName());
    }

    /** 注册 scoped 工厂 kit：kitClass 供元数据/fail-fast 校验（构造器形状不限），factory 每执行 create。 */
    public void registerScoped(String name, Class<?> kitClass, ScopedKitFactory factory) {
        KitDefinition def = new KitDefinition(null, kitClass);
        if (!name.equals(def.getName())) {
            throw new IllegalStateException("scoped kit 注册名不一致: 注册名=" + name + " 注解名=" + def.getName());
        }
        put(def, kitClass.getName());
        factoryScopedNames.add(name);
        scopedFactories.put(name, factory);
        scopedFactoryClasses.put(name, kitClass);
    }

    public void scanPackages(List<String> packages) {
        if (packages == null) {
            return;
        }
        for (String pkg : packages) {
            for (Class<?> clazz : AnnotatedClassScanner.scan(pkg, cn.geelato.core.graal.GraalService.class)) {
                KitDefinition def = new KitDefinition(null, clazz);
                put(def, clazz.getName());
            }
        }
    }

    public Map<String, KitDefinition> getDefinitions() {
        return definitions;
    }

    public Map<String, ScopedKitFactory> getScopedFactories() {
        return scopedFactories;
    }

    /** 无状态 kit 快照（scoped kit 由引擎每执行实例化）。 */
    public Map<String, Object> resolveSingletons() {
        Map<String, Object> singletons = new LinkedHashMap<>();
        for (KitDefinition def : definitions.values()) {
            if (!isScopedKit(def)) {
                singletons.put(def.getName(), def.getInstance() != null ? def.getInstance() : newInstance(def.getClazz()));
            }
        }
        return singletons;
    }

    public Map<String, Class<?>> resolveScopedClasses() {
        Map<String, Class<?>> scoped = new LinkedHashMap<>();
        for (KitDefinition def : definitions.values()) {
            if (isScopedKit(def)) {
                scoped.put(def.getName(), scopedFactoryClasses.getOrDefault(def.getName(), def.getClazz()));
            }
        }
        return scoped;
    }

    /** scoped kit 槽位快照（类 + 注册工厂，版本快照用）。 */
    public Map<String, ScopedKitSlot> resolveScopedSlots() {
        Map<String, ScopedKitSlot> scoped = new LinkedHashMap<>();
        for (KitDefinition def : definitions.values()) {
            if (isScopedKit(def)) {
                scoped.put(def.getName(), new ScopedKitSlot(
                        scopedFactoryClasses.getOrDefault(def.getName(), def.getClazz()),
                        scopedFactories.get(def.getName())));
            }
        }
        return scoped;
    }

    /** scoped 判定：工厂通道注册即 scoped；否则按 (KitContext) 构造器形状隐式推断（实例注册恒为单例）。 */
    private boolean isScopedKit(KitDefinition def) {
        return factoryScopedNames.contains(def.getName()) || def.isScoped();
    }

    /** fail-fast：类型与签名校验，违规抛 IllegalStateException（启动失败并定位）。 */
    public void validate() {
        validate(definitions.values());
    }

    public static void validate(java.util.Collection<KitDefinition> defs) {
        for (KitDefinition def : defs) {
            String where = def.getInstance() != null ? def.getInstance().getClass().getName() : def.getClazz().getName();
            if ((def.getName() == null || def.getName().isBlank())) {
                throw new IllegalStateException("kit " + where + " 的 @GraalService name 为空");
            }
            for (Method m : def.getFunctions()) {
                checkMethod(where, m);
            }
        }
    }

    /** 按可见命名空间过滤输出元数据清单。 */
    public List<GraalServiceDescription> catalog(java.util.function.Predicate<String> namespaceFilter) {
        return catalog(definitions.values(), namespaceFilter);
    }

    public static List<GraalServiceDescription> catalog(java.util.Collection<KitDefinition> defs,
                                                        java.util.function.Predicate<String> namespaceFilter) {
        List<GraalServiceDescription> catalog = new ArrayList<>();
        for (KitDefinition def : defs) {
            if (!namespaceFilter.test(def.getName())) {
                continue;
            }
            GraalServiceDescription d = new GraalServiceDescription();
            d.setServiceName(def.getName());
            d.setDescription(def.getDescription());
            List<GraalFunctionDescription> fns = def.describeFunctions();
            if (!fns.isEmpty()) {
                d.setFunctions(fns);
                catalog.add(d);
            }
        }
        return catalog;
    }

    private void put(KitDefinition def, String source) {
        KitDefinition existing = definitions.get(def.getName());
        if (existing != null) {
            String existingSource = existing.getInstance() != null ? existing.getInstance().getClass().getName() : existing.getClazz().getName();
            throw new IllegalStateException("kit 命名冲突: name=" + def.getName() + " 已由 " + existingSource + " 注册，" + source + " 重复注册");
        }
        definitions.put(def.getName(), def);
    }

    private static void checkMethod(String where, Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            if (!isAllowed(p, PARAM_TYPES) && !p.isPrimitive()) {
                throw new IllegalStateException("kit 方法参数类型不支持 interop: " + where + "#" + m.getName() + "(" + p.getSimpleName() + ")，" +
                        "允许：String/数值/Boolean/Map/List/Date/Object/Value");
            }
        }
        Class<?> r = m.getReturnType();
        if (!isAllowed(r, RETURN_TYPES) && !r.isPrimitive()) {
            throw new IllegalStateException("kit 方法返回类型不支持 interop: " + where + "#" + m.getName() + " 返回 " + r.getSimpleName() +
                    "，允许：String/数值/Boolean/Map/List/Date/Object/void");
        }
    }

    private static boolean isAllowed(Class<?> type, Set<Class<?>> allowed) {
        return allowed.contains(type) || Number.class.isAssignableFrom(type);
    }

    private Object newInstance(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("kit 实例化失败（无状态 kit 须有无参构造器或提供 (KitContext) 构造器）: " + clazz.getName(), e);
        }
    }
}
