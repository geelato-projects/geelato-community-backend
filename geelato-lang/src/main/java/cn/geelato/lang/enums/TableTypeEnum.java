package cn.geelato.lang.enums;

import lombok.Getter;

/**
 * @author diabl
 */

@Getter
public enum TableTypeEnum {
    TABLE("table", "数据库表"), ENTITY("entity", "模型实体"), VIEW("view", "视图");

    private final String code;
    private final String name;

    TableTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

}