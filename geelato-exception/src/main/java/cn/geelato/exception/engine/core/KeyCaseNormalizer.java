package cn.geelato.exception.engine.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤出口键名归一化：snake_case → camelCase（递归数组/对象；无下划线键与标量原样返回）。
 */
public final class KeyCaseNormalizer {

    private KeyCaseNormalizer() {
    }

    public static Object normalize(Object value) {
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalize(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(camel(String.valueOf(entry.getKey())), normalize(entry.getValue()));
            }
            return out;
        }
        return value;
    }

    /** owner_org_id → ownerOrgId；无下划线原样返回。 */
    static String camel(String key) {
        if (key.indexOf('_') < 0) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key.length());
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
