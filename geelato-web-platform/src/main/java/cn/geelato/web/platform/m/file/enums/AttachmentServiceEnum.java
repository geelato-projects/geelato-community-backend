package cn.geelato.web.platform.m.file.enums;

import lombok.Getter;

@Getter
public enum AttachmentServiceEnum {
    LOCAL("本地存储", "local"),
    ALIYUN("阿里云存储", "aliyun");

    private final String label;// 选项内容
    private final String value;// 选项值

    AttachmentServiceEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
