package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.core.graal.GraalService;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 已注册 kit 的定义：实例或类 + 注解元数据（供挂载、fail-fast 校验与 catalog 导出）。
 */
@Getter
public class KitDefinition {

    /** @GraalService name（命名空间）。 */
    private final String name;
    private final String description;
    /** built=true 挂 $gl.{name}；false 挂顶层变量。 */
    private final boolean built;
    /** 无状态 kit：注册的单例实例。 */
    private final Object instance;
    /** scoped kit：每执行实例化的类（构造器为 (KitContext)）。 */
    private final Class<?> clazz;
    private final List<Method> functions = new ArrayList<>();
    /** 整库注入 kit（顶层 $kit）：functions 为拍平的方法清单，hostTree 保留 库别名→类→方法 结构。 */
    private final boolean host;
    private final Map<String, Map<String, List<Method>>> hostTree;

    public KitDefinition(Object instance, Class<?> clazz) {
        GraalService anno = resolveAnnotation(instance != null ? instance.getClass() : clazz);
        if (anno == null) {
            throw new IllegalArgumentException((instance != null ? instance.getClass() : clazz).getName() + " 缺少 @GraalService 注解");
        }
        this.name = anno.name();
        this.description = anno.descrption();
        this.built = Boolean.parseBoolean(anno.built());
        this.instance = instance;
        this.clazz = clazz;
        this.host = false;
        this.hostTree = null;
        for (Method m : (instance != null ? instance.getClass() : clazz).getMethods()) {
            if (m.isAnnotationPresent(GraalFunction.class)) {
                functions.add(m);
            }
        }
    }

    /** 整库注入 kit：name=kit、built=false（挂顶层 $kit），instance 为已构建的容器。 */
    public static KitDefinition hostOf(Object container, Map<String, Map<String, List<Method>>> hostTree) {
        KitDefinition def = new KitDefinition("kit", "整库反射注入（host-packages / META-INF/gl/script-kits.properties）",
                false, container, flatten(hostTree), true, hostTree);
        return def;
    }

    private KitDefinition(String name, String description, boolean built, Object instance,
                          List<Method> functions, boolean host, Map<String, Map<String, List<Method>>> hostTree) {
        this.name = name;
        this.description = description;
        this.built = built;
        this.instance = instance;
        this.clazz = null;
        this.functions.addAll(functions);
        this.host = host;
        this.hostTree = hostTree;
    }

    private static List<Method> flatten(Map<String, Map<String, List<Method>>> hostTree) {
        List<Method> flat = new ArrayList<>();
        for (Map<String, List<Method>> classes : hostTree.values()) {
            for (List<Method> methods : classes.values()) {
                flat.addAll(methods);
            }
        }
        return flat;
    }

    public List<GraalFunctionDescription> describeFunctions() {
        List<GraalFunctionDescription> list = new ArrayList<>();
        if (host) {
            for (Map.Entry<String, Map<String, List<Method>>> lib : hostTree.entrySet()) {
                for (Map.Entry<String, List<Method>> cls : lib.getValue().entrySet()) {
                    for (Method m : cls.getValue()) {
                        String access = lib.getKey() + "." + cls.getKey() + "." + m.getName();
                        GraalFunctionDescription d = new GraalFunctionDescription();
                        d.setName(access);
                        d.setExample(access + "(" + exampleArgs(m) + ")");
                        d.setDescription(m.getDeclaringClass().getName() + "#" + m.getName() + signature(m));
                        list.add(d);
                    }
                }
            }
            return list;
        }
        for (Method m : functions) {
            GraalFunction f = m.getAnnotation(GraalFunction.class);
            GraalFunctionDescription d = new GraalFunctionDescription();
            d.setName(m.getName());
            d.setExample(f.example());
            d.setDescription(f.description());
            list.add(d);
        }
        return list;
    }

    private static String exampleArgs(Method m) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.getParameterCount(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("arg").append(i);
        }
        return sb.toString();
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder("(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params[i].getSimpleName());
        }
        return sb.append("): ").append(m.getReturnType().getSimpleName()).toString();
    }

    /** 脚本侧访问名：built=true 为 $gl.{name}；false 为 ${name}（name 已带 $ 的声明原样兼容）。 */
    public String exposeName() {
        if (built) {
            return "$gl." + name;
        }
        return name.startsWith("$") ? name : "$" + name;
    }

    /** scoped kit 判定：声明了唯一的 public 构造器且参数恰为 KitContext。 */
    public boolean isScoped() {
        if (clazz == null) {
            return false;
        }
        for (java.lang.reflect.Constructor<?> c : clazz.getConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 1 && params[0] == KitContext.class) {
                return true;
            }
        }
        return false;
    }

    private static GraalService resolveAnnotation(Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            if (c.isAnnotationPresent(GraalService.class)) {
                return c.getAnnotation(GraalService.class);
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
