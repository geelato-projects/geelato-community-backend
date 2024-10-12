package cn.geelato.web.platform.m.zxing.enums;

import java.util.Locale;

public enum BarcodeSizeUnitEnum {
    PX, MM, CM, INCH;

    /**
     * 获取默认的单位名称（小写形式），默认为 PX
     *
     * @return 默认的单位名称（小写形式），例如："px"
     */
    public static String getDefault() {
        return PX.name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 根据传入的字符串值获取对应的条码尺寸单位枚举名称（小写形式）
     *
     * @param value                 传入的条码尺寸单位枚举名称
     * @param notFoundReturnDefault 若未找到对应的枚举名称，是否返回默认值
     * @return 如果找到对应的枚举名称，则返回其小写形式；如果未找到且notFoundReturnDefault为true，则返回默认值；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodeSizeUnitEnum sizeUnit : BarcodeSizeUnitEnum.values()) {
            if (sizeUnit.name().equalsIgnoreCase(value)) {
                return sizeUnit.name().toLowerCase(Locale.ENGLISH);
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    /**
     * 获取枚举字符串（不返回默认值）
     *
     * @param value 需要查找的枚举值
     * @return 返回对应的枚举名称（小写形式），若未找到则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
