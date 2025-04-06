package cn.geelato.web.platform.m.zxing.enums;

import java.awt.*;
import java.util.Locale;

public enum BarcodeFontStyleEnum {
    NORMAL("默认", "normal"),
    BOLD("加粗", "bold"),
    ITALIC("斜体", "italic"),
    BOLD_ITALIC("加粗斜体", "bold_italic");

    private final String label;// 选项内容
    private final String value;// 选项值

    BarcodeFontStyleEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    /**
     * 根据字体名称、样式和大小获取字体对象
     *
     * @param familyName 字体名称
     * @param style      字体样式，支持BOLD、ITALIC、BOLD_ITALIC，若传入其他值则默认为普通样式
     * @param size       字体大小
     * @return 返回对应的字体对象
     */
    public static Font getFont(String familyName, String style, int size) {
        Font font = null;
        if (NORMAL.name().equalsIgnoreCase(style)) {
            font = new Font(familyName, Font.PLAIN, size);
        } else if (BOLD.name().equalsIgnoreCase(style)) {
            font = new Font(familyName, Font.BOLD, size);
        } else if (ITALIC.name().equalsIgnoreCase(style)) {
            font = new Font(familyName, Font.ITALIC, size);
        } else if (BOLD_ITALIC.name().equalsIgnoreCase(style)) {
            font = new Font(familyName, Font.BOLD | Font.ITALIC, size);
        } else {
            font = new Font(familyName, Font.BOLD, size);
        }
        return font;
    }

    /**
     * 获取默认的字符串值（小写形式）
     *
     * @return 返回默认值的字符串（小写形式）
     */
    public static String getDefault() {
        return BOLD.name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 根据传入的字符串获取条码字体样式名称（小写形式），可选是否返回默认值
     *
     * @param value                 需要匹配的条码字体样式名称
     * @param notFoundReturnDefault 是否在没有找到匹配的条码字体样式时返回默认值，如果为true则返回默认值，否则返回null
     * @return 如果找到匹配的条码字体样式名称，则返回其小写形式；如果没有找到且isDefault为true，则返回默认的条码字体样式名称的小写形式；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodeFontStyleEnum fontStyle : BarcodeFontStyleEnum.values()) {
            if (fontStyle.name().equalsIgnoreCase(value)) {
                return fontStyle.name().toLowerCase(Locale.ENGLISH);
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    /**
     * 根据传入的字符串值获取枚举名称
     *
     * @param value 需要查找的字符串值
     * @return 返回对应的枚举名称，如果未找到匹配的枚举，则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
