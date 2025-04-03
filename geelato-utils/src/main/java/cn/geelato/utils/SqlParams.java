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
     * 构建参数映射
     * 将输入的参数对（键和值）组合成一个Map对象。参数对需要以键值对的形式依次传入，即参数数量必须为偶数。
     *
     * @param params 参数对，格式为“参数1名,参数1值,参数2名,参数2值,参数3名,参数3值...”
     * @return 返回包含所有参数对的Map对象。如果参数数量不是偶数，则记录一条错误日志，并返回一个空的Map对象。
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
