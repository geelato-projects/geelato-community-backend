package cn.geelato.core.util;

import java.util.Map;

/**
 * Created by hongxq on 2015/12/2.
 */
public class MapUtils {

    /**
     * 从Map中获取指定键对应的值，如果该键不存在或对应的值为null，则返回默认值。
     *
     * @param map          包含键值对的Map对象
     * @param key          要获取的键
     * @param defaultValue 当键不存在或对应的值为null时返回的默认值
     * @return 如果键存在且对应的值不为null，则返回该值转换为字符串后的结果；否则返回默认值
     */
    public static String getOrDefaultString(Map map, String key, String defaultValue) {
        return map.get(key) == null ? defaultValue : map.get(key).toString();
    }

    /**
     * 从Map中获取指定键对应的值，如果该键不存在或对应的值为null，则返回默认值。
     *
     * @param map          包含键值对的Map对象
     * @param key          要获取的键
     * @param defaultValue 当键不存在或对应的值为null时返回的默认值
     * @return 如果键存在且对应的值不为null，则返回该值转换为整数后的结果；否则返回默认值
     * @throws NumberFormatException 如果Map中对应的值无法转换为整数，则抛出此异常
     */
    public static int getOrDefaultInt(Map map, String key, int defaultValue) {
        return map.get(key) == null ? defaultValue : Integer.parseInt(map.get(key).toString());
    }
}
