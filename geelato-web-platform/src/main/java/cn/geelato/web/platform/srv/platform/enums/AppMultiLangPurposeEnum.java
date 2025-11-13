package cn.geelato.web.platform.srv.platform.enums;

import lombok.Getter;

@Getter
public enum AppMultiLangPurposeEnum {
    WEBAPP("前端", "webapp"),
    ENDPOINT("后端", "endpoint");

    private final String label;// 选项内容
    private final String value;// 选项值

    AppMultiLangPurposeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
