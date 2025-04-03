package cn.geelato.web.platform.m.base.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
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
}
