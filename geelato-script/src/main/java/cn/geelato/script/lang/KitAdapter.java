package cn.geelato.script.lang;

import cn.geelato.core.graal.GraalFunction;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * guest 侧 kit 适配：按实参个数选择同名重载，缺省尾参补 null 后反射调用——
 * GraalVM host 调用严格校验 arity，kit 方法的可选尾参（filter/orderBy/params 等）经此放行。
 * 仅暴露 @GraalFunction 方法，表面与 HostAccess 注解白名单一致。
 */
public final class KitAdapter implements ProxyObject {

    private final Object kit;
    private final Map<String, List<Method>> functions;
    /** host kit（第三方类反射）返回值须经 GuestValues 包装——其方法体无法像内置 kit 一样自行包装。 */
    private final boolean wrapReturns;

    private KitAdapter(Object kit, Map<String, List<Method>> functions, boolean wrapReturns) {
        this.kit = kit;
        this.functions = functions;
        this.wrapReturns = wrapReturns;
    }

    public static KitAdapter of(Object kit) {
        Map<String, List<Method>> functions = new LinkedHashMap<>();
        for (Class<?> c = kit.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(GraalFunction.class)) {
                    m.setAccessible(true);
                    functions.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
                }
            }
        }
        return new KitAdapter(kit, functions, false);
    }

    /** host kit 构建：方法清单（已过滤）按名分组为重载列表；receiver 为实例或 null（纯静态）。 */
    public static KitAdapter ofHost(Object receiver, List<Method> methods) {
        Map<String, List<Method>> functions = new LinkedHashMap<>();
        for (Method m : methods) {
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
                // public 类的 public 方法无需提权即可反射调用
            }
            functions.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        return new KitAdapter(receiver, functions, true);
    }

    public static boolean adaptable(Object kit) {
        for (Method m : kit.getClass().getMethods()) {
            if (m.isAnnotationPresent(GraalFunction.class)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getMember(String key) {
        List<Method> overloads = functions.get(key);
        if (overloads == null) {
            return null;
        }
        return (ProxyExecutable) args -> invoke(overloads, args == null ? new Value[0] : args);
    }

    @Override
    public Object getMemberKeys() {
        return org.graalvm.polyglot.proxy.ProxyArray.fromArray(functions.keySet().toArray());
    }

    @Override
    public boolean hasMember(String key) {
        return functions.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("不支持修改 kit 方法");
    }

    private Object invoke(List<Method> overloads, Value... args) {
        Method target = null;
        for (Method m : overloads) {
            if (m.getParameterCount() == args.length) {
                target = m;
                break;
            }
        }
        if (target == null) {
            for (Method m : overloads) {
                if (target == null || m.getParameterCount() > target.getParameterCount()) {
                    target = m;
                }
            }
        }
        Class<?>[] types = target.getParameterTypes();
        Object[] padded = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Value raw = i < args.length ? args[i] : null;
            padded[i] = convert(raw, types[i]);
        }
        try {
            return wrapReturns ? GuestValues.toGuest(target.invoke(kit, padded)) : target.invoke(kit, padded);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw cause instanceof RuntimeException runtime ? runtime : new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("kit 方法调用失败: " + target.getName(), e);
        }
    }

    /** 按 kit 方法形参类型转换实参：Value 形参保持原生，String 形参转文本，其余走 Java 归一值。 */
    private static Object convert(Value raw, Class<?> type) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        if (type == Value.class) {
            return raw;
        }
        Object o = ValueUnwrapper.toJavaObject(raw);
        if (type == String.class) {
            return o == null ? null : String.valueOf(o);
        }
        if (o == null) {
            return null;
        }
        if ((type == int.class || type == long.class || type == Integer.class || type == Long.class) && o instanceof Number n) {
            if (type == int.class || type == Integer.class) {
                return n.intValue();
            }
            return n.longValue();
        }
        if ((type == double.class || type == Double.class) && o instanceof Number n) {
            return n.doubleValue();
        }
        if ((type == boolean.class || type == Boolean.class) && o instanceof Boolean b) {
            return b;
        }
        return o;
    }
}
