package cn.geelato.web.platform.m.base.enums;

import java.util.List;
import java.util.Locale;

public enum ResponseParamTypeEnum {
    NULL,
    STRING,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY;

    /**
     * 判断是否有返回参数
     *
     * @param value
     * @return
     */
    public static boolean hasResult(String value) {
        List<String> list = List.of(STRING.name().toLowerCase(Locale.ENGLISH),
                NUMBER.name().toLowerCase(Locale.ENGLISH),
                BOOLEAN.name().toLowerCase(Locale.ENGLISH),
                OBJECT.name().toLowerCase(Locale.ENGLISH),
                ARRAY.name().toLowerCase(Locale.ENGLISH));
        return list.contains(value);
    }
}
