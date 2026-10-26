package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.core.graal.GraalServiceDescription;
import cn.geelato.script.lang.HostKitContainer;
import cn.geelato.script.lang.KitAdapter;
import cn.geelato.utils.AnnotatedClassScanner;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注解 kit 注册表（实例级，不挂 GraalManager 全局单例）：
 * 注册通道 —— register(实例)（Spring Bean 优先）/ register(类) / scanPackages(类路径扫描 newInstance 兜底）
 * / scanHostPackages(整库反射注入，无需注解，挂顶层 $kit)；
 * 注册完成即 fail-fast（命名冲突、方法参数/返回类型 interop 兼容白名单）。
 */
public class KitRegistry {

    private static final Logger logger = LoggerFactory.getLogger(KitRegistry.class);

    private static final Set<Class<?>> PARAM_TYPES = Set.of(
            String.class, Object.class, Map.class, List.class, Value.class, Date.class, Boolean.class);
    private static final Set<Class<?>> RETURN_TYPES = Set.of(
            String.class, Object.class, Map.class, List.class, Value.class, Date.class, Boolean.class, void.class, Void.class);
    /** 整库注入危险类（与 SandboxContextFactory denyAccess 同集）。 */
    private static final Set<String> DENIED_HOST_CLASSES = Set.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.Class", "java.lang.ClassLoader", "java.lang.Thread");

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

    /**
     * 整库反射注入：按 库别名→根包 扫描具体类，暴露 interop 兼容的公共方法
     * （静态直接暴露；实例方法须有 public 无参构造），合并注册为顶层 $kit。
     */
    public void scanHostPackages(Map<String, String> aliasToPackage, List<String> excludes) {
        if (aliasToPackage == null || aliasToPackage.isEmpty()) {
            return;
        }
        Set<String> excluded = new LinkedHashSet<>();
        if (excludes != null) {
            excludes.forEach(x -> {
                if (x != null && !x.isBlank()) {
                    excluded.add(x.trim());
                }
            });
        }
        HostKitContainer container = new HostKitContainer();
        Map<String, Map<String, List<Method>>> tree = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : aliasToPackage.entrySet()) {
            String alias = e.getKey();
            String pkg = e.getValue();
            Map<String, KitAdapter> classAdapters = new LinkedHashMap<>();
            Map<String, List<Method>> libTree = new LinkedHashMap<>();
            for (Class<?> clazz : AnnotatedClassScanner.scanAll(pkg)) {
                if (!isInjectableClass(clazz) || excluded.contains(clazz.getSimpleName()) || excluded.contains(clazz.getName())) {
                    continue;
                }
                if (DENIED_HOST_CLASSES.contains(clazz.getName())) {
                    throw new IllegalStateException("host-packages 拒绝危险类: " + clazz.getName());
                }
                HostClassBinding binding = bindHostClass(clazz);
                if (binding == null || binding.methods().isEmpty()) {
                    continue;
                }
                String member = decapitalize(clazz.getSimpleName());
                if (libTree.containsKey(member)) {
                    throw new IllegalStateException("host-packages 别名 " + alias + " 下类名冲突: " + member
                            + "（" + member + " 已由 " + libTree.get(member).get(0).getDeclaringClass().getName()
                            + " 占用，" + clazz.getName() + " 重复；可拆分为多个别名子包配置）");
                }
                classAdapters.put(member, KitAdapter.ofHost(binding.receiver(), binding.methods()));
                libTree.put(member, binding.methods());
            }
            if (libTree.isEmpty()) {
                throw new IllegalStateException("host-packages 别名 " + alias + "（包 " + pkg + "）未扫到任何可注入类："
                        + "包名不在 classpath、或所有类/方法均不满足暴露条件（被 exclude、无兼容方法、非具体类）");
            }
            container.addLibrary(alias, classAdapters);
            tree.put(alias, libTree);
        }
        put(KitDefinition.hostOf(container, tree), "host-packages");
    }

    private static boolean isInjectableClass(Class<?> clazz) {
        return !clazz.isInterface() && !clazz.isAnnotation() && !clazz.isEnum() && !clazz.isPrimitive()
                && !clazz.isSynthetic() && !Modifier.isAbstract(clazz.getModifiers());
    }

    private static HostClassBinding bindHostClass(Class<?> clazz) {
        Object instance = null;
        boolean instantiable = false;
        try {
            clazz.getConstructor();
            instantiable = true;
        } catch (NoSuchMethodException ignored) {
            // 纯静态工具类无无参构造，仅暴露静态方法
        }
        List<Method> methods = new ArrayList<>();
        int skipped = 0;
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (!interopCompatible(m)) {
                skipped++;
                continue;
            }
            if (Modifier.isStatic(m.getModifiers())) {
                methods.add(m);
            } else if (instantiable) {
                if (instance == null) {
                    try {
                        instance = clazz.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException("host-packages 类实例化失败: " + clazz.getName(), ex);
                    }
                }
                methods.add(m);
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            logger.warn("host-packages 类 {} 暴露 {} 个方法，跳过 {} 个（签名不兼容 interop 或需实例化但无无参构造）",
                    clazz.getName(), methods.size(), skipped);
        }
        return new HostClassBinding(methods, instance);
    }

    private static boolean interopCompatible(Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            if (!isAllowed(p, PARAM_TYPES) && !p.isPrimitive()) {
                return false;
            }
        }
        Class<?> r = m.getReturnType();
        return isAllowed(r, RETURN_TYPES) || r.isPrimitive();
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private record HostClassBinding(List<Method> methods, Object receiver) {
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
