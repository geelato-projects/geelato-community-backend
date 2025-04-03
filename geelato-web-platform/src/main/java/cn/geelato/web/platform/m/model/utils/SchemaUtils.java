package cn.geelato.web.platform.m.model.utils;

import cn.geelato.utils.StringUtils;

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
 * Schema Util
 */
public class SchemaUtils implements Serializable {

    /**
     * 根据提供的类类型和包含数据的列表，构建对应的数据对象列表。
     *
     * @param <T>    泛型类型，表示要构建的数据对象的类型。
     * @param tClass Class对象，表示要构建的数据对象的类型。
     * @param list   包含数据的列表，列表中的每个元素都是一个Map，Map的键表示数据对象的属性名，值表示对应的属性值。
     * @return 返回构建好的数据对象列表。
     */
    public static <T> List<T> buildData(Class<T> tClass, List<Map<String, Object>> list) {
        List<T> lists = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (Map<String, Object> map : list) {
                lists.add(buildData(tClass, map));
            }
        }
        return lists;
    }

    /**
     * 根据提供的类类型和包含数据的Map，构建对应的数据对象。
     *
     * @param <T>    泛型类型，表示要构建的数据对象的类型。
     * @param tClass Class对象，表示要构建的数据对象的类型。
     * @param map    包含数据的Map，Map的键表示数据对象的属性名，值表示对应的属性值。
     * @return 返回构建好的数据对象。
     * @throws RuntimeException 如果在构建数据对象过程中发生异常，则抛出此异常。
     */
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
                String fieldName = org.apache.commons.lang3.StringUtils.isNotBlank(value.getKey()) ? StringUtils.toCamelCase(key) : null;
                if (org.apache.commons.lang3.StringUtils.isNotBlank(fieldName)) {
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
