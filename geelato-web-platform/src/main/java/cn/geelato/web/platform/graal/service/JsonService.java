package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

@GraalService(name = "json", built = "true")
public class JsonService {
    /**
     * 将对象序列化为JSON字符串
     *
     * @param object 可以是任意Java对象
     * @return JSON字符串，如果输入为null则返回null
     */
    public String toString(Object object) {
        return object == null ? null : JSON.toJSONString(object, SerializerFeature.WriteMapNullValue);
    }

    /**
     * 将JSON字符串反序列化为Java对象
     *
     * @param jsonString 合法的JSON字符串
     * @return 解析后的Java对象，如果输入为空则返回null
     * @throws IllegalArgumentException 如果jsonString不是合法的JSON
     */
    public Object toObject(String jsonString) {
        if (StringUtils.isBlank(jsonString)) {
            return null;
        }
        try {
            return JSON.parse(jsonString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON string: " + jsonString, e);
        }
    }

    /**
     * 智能解析方法（增强版）
     *
     * @param input 可以是String/JSONObject/普通Java对象
     * @return 解析后的Java对象
     * @throws IllegalArgumentException 如果输入无法解析
     */
    public Object toParse(Object input) {
        if (input == null) {
            return null;
        }
        // 已经是JSONObject或Map直接返回
        if (input instanceof JSONObject || input instanceof java.util.Map) {
            return input;
        }
        // 字符串处理
        if (input instanceof String) {
            String str = (String) input;
            if (StringUtils.isBlank(str)) {
                return null;
            }
            return toObject(str); // 复用toObject方法
        }
        // 其他类型先序列化再解析
        try {
            String jsonString = toString(input);
            return JSON.parse(jsonString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse object: " + input, e);
        }
    }
}
