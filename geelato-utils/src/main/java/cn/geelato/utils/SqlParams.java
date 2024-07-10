package cn.geelato.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;

/**
 * @author geemeta
 */
public class SqlParams extends SimpleBindings {

    private static final Logger logger = LoggerFactory.getLogger(SqlParams.class);

    /**
     * 构建参数，
     *
     * @param params 参数params的数量需为偶数，格式如：参数1名,参数1值,参数2名,参数2值,参数3名,参数3值...
     * @return
     */
    public static Map<String, Object> map(String... params) {
        if (params.length % 2 != 0) {
            logger.error("参数params的数量需为偶数，格式如：参数1名,参数1值,参数2名,参数2值,参数3名,参数3值...");
        }
        Map<String, Object> map = new HashMap<>(params.length / 2);
        int i = 0;
        String key = "";
        for (String param : params) {
            if (i % 2 == 0) {
                key = param;
            } else {
                map.put(key, param);
            }
            i++;
        }
        return map;
    }
}
