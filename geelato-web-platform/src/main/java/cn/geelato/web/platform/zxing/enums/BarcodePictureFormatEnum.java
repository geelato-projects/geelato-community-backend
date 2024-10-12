package cn.geelato.web.platform.zxing.enums;

import lombok.Getter;

@Getter
public enum BarcodePictureFormatEnum {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    ICO("image/x-icon", "ico"),
    SVG("image/svg+xml", "svg");

    public final String contentType;
    public final String extension;

    private BarcodePictureFormatEnum(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /**
     * 获取默认的图片格式名称（小写形式）
     *
     * @return 返回默认的图片格式名称（小写形式），默认为 PNG
     */
    public static String getDefault() {
        return PNG.GIF.getExtension();
    }


    /**
     * 根据传入的字符串获取对应的条码图片格式枚举名称（小写形式）
     *
     * @param value                 需要查找的条码图片格式枚举名称
     * @param notFoundReturnDefault 如果未找到对应的枚举值，是否返回默认值
     * @return 如果找到对应的枚举值，则返回其名称的小写形式；如果未找到且notFoundReturnDefault为true，则返回默认值；否则返回null
     */
    public static String getEnum(String value, boolean notFoundReturnDefault) {
        for (BarcodePictureFormatEnum pictureFormat : values()) {
            if (pictureFormat.getExtension().equalsIgnoreCase(value)) {
                return pictureFormat.getExtension();
            }
        }
        return notFoundReturnDefault ? getDefault() : null;
    }

    public static String getContentType(String value) {
        for (BarcodePictureFormatEnum pictureFormat : values()) {
            if (pictureFormat.getExtension().equalsIgnoreCase(value)) {
                return pictureFormat.getContentType();
            }
        }
        return PNG.getContentType();
    }

    /**
     * 获取枚举字符串，不返回默认值
     *
     * @param value 需要获取的枚举名称
     * @return 返回对应的枚举名称（小写），若未找到则返回null
     */
    public static String getEnum(String value) {
        return getEnum(value, false);
    }
}
