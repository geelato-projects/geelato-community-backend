package cn.geelato.core.util;

import org.apache.logging.log4j.util.Strings;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author diabl
 * @description: Schema Util
 * @date 2023/6/16 12:00
 */
public class SchemaUtils implements Serializable {

    public static <T> List<T> buildData(Class<T> tClass, List<Map<String, Object>> list) {
        List<T> lists = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (Map<String, Object> map : list) {
                lists.add(buildData(tClass, map));
            }
        }
        return lists;
    }

    public static <T> T buildData(Class<T> tClass, Map<String, Object> map) {
        Constructor<?> constructor = null;
        T object = null;
        try {
            // 创建实体对象
            constructor = tClass.getConstructor();
            object = (T) constructor.newInstance();
            // 所有属性
            for (Map.Entry<String, Object> value : map.entrySet()) {
                String key = value.getKey().toLowerCase(Locale.ENGLISH).trim();
                if (List.of(new String[]{"null"}).contains(key)) {
                    continue;
                }
                String fieldName = Strings.isNotBlank(value.getKey()) ? StringUtils.toCamelCase(key) : null;
                if (Strings.isNotBlank(fieldName)) {
                    Field field = tClass.getDeclaredField(fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        field.set(object, value.getValue() != null ? value.getValue().toString() : null);
                    }
                }
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        return object;
    }
}
