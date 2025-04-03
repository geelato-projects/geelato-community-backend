package cn.geelato.web.platform.m.script.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 */
@Getter
public enum ResponseParamTypeEnum {
    STRING("字符串", "string"),
    NUMBER("数值", "number"),
    BOOLEAN("布尔值", "boolean"),
    OBJECT("对象", "object"),
    ARRAY("数组", "array");

    private final String label;// 选项内容
    private final String value;// 选项值

    ResponseParamTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
