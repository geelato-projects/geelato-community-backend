package cn.geelato.web.platform.srv.base.enums;

public enum ApplicationPurposeEnum {
    INSIDE("企业内部", "inside"),
    OUTSIDE("企业外部", "outside"),
    SIDE("企业内外部", "side");

    private final String label;// 选项内容
    private final String value;// 选项值

    ApplicationPurposeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
