package cn.geelato.web.platform.m.base.enums;

public enum ModelColumnPclEnum {
    STRICT("严格", "strict"),
    LOOSE("宽松", "loose");

    private final String label;// 选项内容
    private final String value;// 选项值

    ModelColumnPclEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
