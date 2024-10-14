package cn.geelato.web.platform.m.zxing.enums;

import java.util.Locale;

public enum BarcodeFontAlignEnum {
    LEFT, CENTER, RIGHT;

    /**
     * 判断传入的字符串是否与枚举值 LEFT 的名称相等（不区分大小写）
     *
     * @param value 需要判断的字符串
     * @return 如果传入的字符串与枚举值 LEFT 的名称相等（不区分大小写），则返回 true；否则返回 false
     */
    public static Boolean isLeft(String value) {
        return LEFT.name().equalsIgnoreCase(value);
    }

    /**
     * 判断传入的字符串是否与枚举值 CENTER 的名称相等（不区分大小写）
     *
     * @param value 需要判断的字符串
     * @return 如果传入的字符串与枚举值 CENTER 的名称相等（不区分大小写），则返回 true；否则返回 false
     */
    public static Boolean isCenter(String value) {
        return CENTER.name().equalsIgnoreCase(value);
    }

    /**
     * 判断传入的字符串是否与枚举值 RIGHT 的名称相等（不区分大小写）
     *
     * @param value 需要判断的字符串
     * @return 如果传入的字符串与枚举值 RIGHT 的名称相等（不区分大小写），则返回 true；否则返回 false
     */
    public static Boolean isRight(String value) {
        return RIGHT.name().equalsIgnoreCase(value);
    }

    /**
     * 获取默认的对齐方式名称（小写）
     *
     * @return 默认对齐方式名称（小写）
     */
    public static String getDefault() {
        return CENTER.name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 根据传入的字符串获取对应的条码字体对齐枚举名称（小写形式），若未找到则根据是否返回默认值来返回不同的结果
     *
     * @param value                 传入的条码字体对齐枚举名称
     * @param notFoundReturnDefault 若未找到对应的枚举名称，是否返回默认值，true为返回默认值，false为返回null
     * @return 若找到对应的枚举名称，则返回其小写形式；若未找到且notFoundReturnDefault为true，则返回默认的对齐枚举名称的小写形式；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodeFontAlignEnum align : BarcodeFontAlignEnum.values()) {
            if (align.name().equalsIgnoreCase(value)) {
                return align.name().toLowerCase(Locale.ENGLISH);
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    /**
     * 获取枚举值对应的字符串（不返回默认值）
     *
     * @param value 需要查找的枚举值名称
     * @return 对应的枚举值名称（小写），若未找到则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
