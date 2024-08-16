package cn.geelato.web.platform.enums;

import cn.geelato.utils.StringUtils;

/**
 * @author diabl
 */
public enum SysConfigValueTypeEnum {
    STRING("字符串", "string"),
    NUMBER("数值", "number"),
    BOOLEAN("布尔值", "boolean"),
    DATETIME("日期时间", "datetime"),
    JSON("Json", "json"),
    ENCRYPT("加密", "encrypt"),
    BASE64("上传(Base64)", "base64"),
    UPLOAD("上传", "upload");

    private final String label;// 选项内容
    private final String value;// 选项值

    SysConfigValueTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (StringUtils.isNotBlank(value)) {
            for (SysConfigValueTypeEnum enums : SysConfigValueTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}