package cn.geelato.core.util;

import cn.geelato.lang.meta.Col;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author diabl
 */
public class ClassUtils {

    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        if (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            fields.addAll(getAllFields(clazz.getSuperclass()));
        }

        return fields;
    }

    public static Object toMeta(Class<?> clazz, Map<String, Object> map) {
        HashMap<String, Object> newObject = new HashMap<>();
        List<Field> fields = ClassUtils.getAllFields(clazz);
        for (Field f : fields) {
            Col col = f.getAnnotation(Col.class);
            String colName = f.getName();
            if (col != null) {
                colName = col.name();
            }
            newObject.put(f.getName(), map.get(colName));
        }

        return newObject;
    }

    public static Object toMapperDBObject(Object obj) {
        List<Field> fields = ClassUtils.getAllFields(obj.getClass());
        HashMap<String, Object> newObject = new HashMap<>();
        for (Field f : fields) {
            Col col = f.getAnnotation(Col.class);
            String colName = f.getName();
            if (col != null) {
                colName = col.name();
            }
            Object fieldValue;
            try {
                f.setAccessible(true);
                fieldValue = f.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            newObject.put(colName, fieldValue);
        }

        return newObject;
    }
}
