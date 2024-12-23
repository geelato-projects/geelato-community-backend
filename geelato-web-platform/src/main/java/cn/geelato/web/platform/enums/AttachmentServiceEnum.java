package cn.geelato.web.platform.enums;

import lombok.Getter;

@Getter
public enum AttachmentServiceEnum {

    OSS_LOCAL("本地存储", "local"),
    OSS_ALI("阿里云OSS", "aliyun");

    private final String label;// 选项内容
    private final String value;// 选项值

    AttachmentServiceEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
