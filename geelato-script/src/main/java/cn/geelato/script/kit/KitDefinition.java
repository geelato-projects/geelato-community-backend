package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalFunctionDescription;
import cn.geelato.core.graal.GraalService;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
        for (Method m : (instance != null ? instance.getClass() : clazz).getMethods()) {
            if (m.isAnnotationPresent(GraalFunction.class)) {
                functions.add(m);
            }
        }
    }

    public List<GraalFunctionDescription> describeFunctions() {
        List<GraalFunctionDescription> list = new ArrayList<>();
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
