package cn.geelato.lang.enums;

/**
 * @author diabl
 * @description: 表格视图，类型枚举
 */
public enum ViewTypeEnum {
    DEFAULT("default", "默认视图"), CUSTOM("custom", "自定义视图");

    private String code;
    private String name;

    ViewTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
