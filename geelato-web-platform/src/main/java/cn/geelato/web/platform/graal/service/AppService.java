package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.security.App;
import cn.geelato.security.SecurityContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@GraalService(name = "app",built = "true", descrption = "上下文应用相关")
public class AppService  implements ProxyObject {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of();
    private static final Map<String, Field> ALLOWED_FIELD_CACHE;

    static {
        List<Class<?>> allClasses = new ArrayList<>();
        Class<?> currentClass = App.class;
        while (currentClass != null && currentClass != Object.class) {
            allClasses.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        ALLOWED_FIELD_CACHE = allClasses.stream()
                .flatMap(clz -> Arrays.stream(clz.getDeclaredFields()))
                .filter(field -> !FORBIDDEN_FIELDS.contains(field.getName()))
                .peek(field -> field.setAccessible(true))
                .collect(Collectors.toMap(
                        Field::getName,
                        field -> field,
                        (existing, replacement) -> replacement // 子类字段覆盖父类
                ));
    }

    @Override
    public Object getMember(String key) {
        if (FORBIDDEN_FIELDS.contains(key)) {
            return null;
        }

        App currentApp = SecurityContext.getCurrentApp();
        if (currentApp == null) {
            return null;
        }

        Field field = ALLOWED_FIELD_CACHE.get(key);
        if (field == null) {
            return null;
        }

        try {
            return field.get(currentApp);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object getMemberKeys() {
        return ALLOWED_FIELD_CACHE.keySet().toArray(new String[0]);
    }

    @Override
    public boolean hasMember(String key) {
        if (FORBIDDEN_FIELDS.contains(key)) {
            return false;
        }
        return ALLOWED_FIELD_CACHE.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("不支持修改属性");
    }
}
