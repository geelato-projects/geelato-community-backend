package cn.geelato.web.platform.srv.base.enums;

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
     * <p>
     * 该方法用于判断给定的字符串是否表示一个有效的返回参数类型。
     *
     * @param value 需要判断的字符串
     * @return 如果该字符串表示一个有效的返回参数类型，则返回true；否则返回false
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
