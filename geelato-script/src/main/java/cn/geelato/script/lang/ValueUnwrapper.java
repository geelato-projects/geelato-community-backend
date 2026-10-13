package cn.geelato.script.lang;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * polyglot Value 与 Java 类型互转（步骤间接力 $input 与 kit 参数转换共用）。
 */
public final class ValueUnwrapper {

    private ValueUnwrapper() {
    }

    /** guest 值 -> Java（List/Map/String/Long/Double/Boolean/null，递归）。 */
    public static Object toJavaObject(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInLong() && !value.toString().contains(".")) {
                try {
                    return value.asLong();
                } catch (Exception ignored) {
                }
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(toJavaObject(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, toJavaObject(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }

    public static Map<String, Object> toMap(Value value) {
        Object o = toJavaObject(value);
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
            return map;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> toList(Value value) {
        Object o = toJavaObject(value);
        if (o instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        if (o != null) {
            List<Object> list = new ArrayList<>();
            list.add(o);
            return list;
        }
        return new ArrayList<>();
    }

    public static String toNullableString(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        Object o = toJavaObject(value);
        return o == null ? null : String.valueOf(o);
    }
}
