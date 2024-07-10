package cn.geelato.utils;

import java.util.Map;

/**
 * Created by hongxq on 2015/12/2.
 *
 */
public class MapUtils {

    public static String getOrDefaultString(Map map, String key, String defaultValue) {
        return map.get(key) == null ? defaultValue : map.get(key).toString();
    }

    public static int getOrDefaultInt(Map map, String key, int defaultValue) {
        return map.get(key) == null ? defaultValue : Integer.parseInt(map.get(key).toString());
    }
}
