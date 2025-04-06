package cn.geelato.web.platform.m.security.enums;

public enum OrgCategoryEnum {
    INSIDE("内部", "inside"),
    OUTSIDE("外部", "outside"),
    VIRTUAL("虚拟", "virtual");

    private final String label;// 选项内容
    private final String value;// 选项值

    OrgCategoryEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
