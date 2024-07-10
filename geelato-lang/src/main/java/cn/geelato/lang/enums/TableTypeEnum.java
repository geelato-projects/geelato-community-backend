package cn.geelato.lang.enums;

/**
 * @author diabl
 */

public enum TableTypeEnum {
    TABLE("table", "数据库表"), ENTITY("entity", "模型实体"), VIEW("view", "视图");

    private final String code;
    private final String name;

    TableTypeEnum(String code, String name) {
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