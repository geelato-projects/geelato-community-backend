package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum TableForeignAction {
    RESTRICT("在子表有关联记录的情况下父表不能更新", "RESTRICT"),
    NO_ACTION("在子表有关联记录的情况下父表不能更新", "NO ACTION"),
    SET_NULL("父表在更新或者删除的时候，子表的对应字段被SET NULL", "SET NULL"),
    CASCADE("父表在更新或者删除时，更新或者删除子表对应记录", "CASCADE");

    private final String label;
    private final String value;

    TableForeignAction(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
