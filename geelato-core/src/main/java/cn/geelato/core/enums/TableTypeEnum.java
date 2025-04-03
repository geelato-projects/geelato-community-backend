package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum TableTypeEnum {
    TABLE("table", "数据库表"),
    ENTITY("entity", "模型实体"),
    VIEW("view", "视图");

    private final String value;
    private final String label;

    TableTypeEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
