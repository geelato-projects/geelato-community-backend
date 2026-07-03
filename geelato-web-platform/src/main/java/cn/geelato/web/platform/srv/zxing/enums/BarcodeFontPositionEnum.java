package cn.geelato.web.platform.srv.zxing.enums;

import java.util.Locale;

public enum BarcodeFontPositionEnum {
    TOP("顶部", "top"),
    BOTTOM("底部", "bottom");

    private final String label;// 选项内容
    private final String value;// 选项值

    BarcodeFontPositionEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    /**
     * 判断传入的字符串是否与枚举值 TOP 的名称相等（不区分大小写）
     *
     * @param value 需要判断的字符串
     * @return 如果传入的字符串与枚举值 TOP 的名称相等（不区分大小写），则返回 true；否则返回 false
     */
    public static boolean isTop(String value) {
        return TOP.name().equalsIgnoreCase(value);
    }

    /**
     * 判断传入的字符串是否与枚举值 BOTTOM 的名称相等（不区分大小写）
     *
     * @param value 需要判断的字符串
     * @return 如果传入的字符串与枚举值 BOTTOM 的名称相等（不区分大小写），则返回 true；否则返回 false
     */
    public static boolean isBottom(String value) {
        return BOTTOM.name().equalsIgnoreCase(value);
    }

    /**
     * 设置默认对齐方式为BOTTOM，并返回其名称的小写形式
     *
     * @return BOTTOM名称的小写形式
     */
    public static String getDefault() {
        return BOTTOM.name().toLowerCase(Locale.ENGLISH);
    }


    /**
     * 根据传入的字符串值获取对应的条码字体位置枚举名称（小写形式），若未找到对应的枚举且notFoundReturnDefault为true，则返回默认值。
     *
     * @param value                 传入的需要查找的字符串值
     * @param notFoundReturnDefault 若未找到对应的枚举时，是否返回默认值
     * @return 如果找到对应的枚举，则返回其名称的小写形式；如果未找到且notFoundReturnDefault为true，则返回默认值；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodeFontPositionEnum position : BarcodeFontPositionEnum.values()) {
            if (position.name().equalsIgnoreCase(value)) {
                return position.name().toLowerCase(Locale.ENGLISH);
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    /**
     * 根据给定的字符串值获取枚举名称（小写形式），若未找到对应的枚举则返回null。
     *
     * @param value 需要查找的字符串值
     * @return 对应的枚举名称（小写形式），若未找到对应的枚举则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
