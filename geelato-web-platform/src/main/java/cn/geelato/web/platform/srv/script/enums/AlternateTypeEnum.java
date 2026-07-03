package cn.geelato.web.platform.srv.script.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum AlternateTypeEnum {
    REQUEST("请求", "request"),
    RESPONSE("响应", "response");

    private final String label;// 选项内容
    private final String value;// 选项值

    AlternateTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
