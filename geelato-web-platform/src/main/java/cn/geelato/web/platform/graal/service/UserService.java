package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@GraalService(name = "user", built = "true", descrption = "用户相关")
public class UserService implements ProxyObject {

    private static final Set<String> FORBIDDEN_FIELDS = new HashSet<>(Arrays.asList(
            "defaultOrg", "tenant", "userOrgs", "userRoles", "dataPermissions", "elementPermissions"
    ));

    private static final Map<String, Field> ALLOWED_FIELD_CACHE;

    static {
        List<Class<?>> allClasses = new ArrayList<>();
        Class<?> currentClass = User.class;
        while (currentClass != null && currentClass != Object.class) {
            allClasses.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }

        // 收集所有非黑名单字段（含父类）
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

        User currentUser = SecurityContext.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        Field field = ALLOWED_FIELD_CACHE.get(key);
        if (field == null) {
            return null;
        }

        try {
            return field.get(currentUser);
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