package cn.geelato.web.platform.m.zxing.enums;

import com.google.zxing.BarcodeFormat;
import lombok.Getter;

@Getter
public enum BarcodeTypeEnum {
    AZTEC("AZTEC", BarcodeFormat.AZTEC),
    CODABAR("CODABAR", BarcodeFormat.CODABAR),
    CODE_39("CODE_39", BarcodeFormat.CODE_39),
    CODE_93("CODE_93", BarcodeFormat.CODE_93),
    CODE_128("CODE_128", BarcodeFormat.CODE_128),
    DATA_MATRIX("DATA_MATRIX", BarcodeFormat.DATA_MATRIX),
    EAN_8("EAN_8", BarcodeFormat.EAN_8),
    EAN_13("EAN_13", BarcodeFormat.EAN_13),
    ITF("ITF", BarcodeFormat.ITF),
    MAXICODE("MAXICODE", BarcodeFormat.MAXICODE),
    PDF_417("PDF_417", BarcodeFormat.PDF_417),
    QR_CODE("QR_CODE", BarcodeFormat.QR_CODE),
    RSS_14("RSS_14", BarcodeFormat.RSS_14),
    RSS_EXPANDED("RSS_EXPANDED", BarcodeFormat.RSS_EXPANDED),
    UPC_A("UPC_A", BarcodeFormat.UPC_A),
    UPC_E("UPC_E", BarcodeFormat.UPC_E),
    UPC_EAN_EXTENSION("UPC_EAN_EXTENSION", BarcodeFormat.UPC_EAN_EXTENSION);

    private final String value;// 选项名称
    private final BarcodeFormat barcodeFormat;// 选项值

    /**
     * 构造函数
     *
     * @param value         条码类型值
     * @param barcodeFormat 条码格式枚举
     */
    BarcodeTypeEnum(String value, BarcodeFormat barcodeFormat) {
        this.value = value;
        this.barcodeFormat = barcodeFormat;
    }

    /**
     * 根据条码类型值获取条码格式
     *
     * @param value 条码类型值
     * @return 条码格式枚举，如果未找到对应的条码类型值则返回null
     */
    public static BarcodeFormat getFormatByValue(String value) {
        for (BarcodeTypeEnum type : values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type.getBarcodeFormat();
            }
        }
        return null;
    }

    /**
     * 获取默认的条码类型值
     *
     * @return 返回默认的条码类型值 CODE_128
     */
    public static String getDefault() {
        return CODE_128.getValue();
    }

    /**
     * 根据传入的字符串值获取对应的条码类型枚举值，若未找到且notFoundReturnDefault为true，则返回默认值。
     *
     * @param value                 传入的条码类型字符串值
     * @param notFoundReturnDefault 若未找到对应的枚举值，是否返回默认值
     * @return 如果找到对应的枚举值，则返回其值；如果未找到且notFoundReturnDefault为true，则返回默认值；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodeTypeEnum type : values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type.getValue();
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    /**
     * 根据给定的字符串值获取枚举项，不返回默认值。
     *
     * @param value 需要查找的字符串值
     * @return 返回对应的枚举项值，如果未找到则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
