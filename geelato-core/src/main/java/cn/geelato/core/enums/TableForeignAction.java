package cn.geelato.core.enums;

/**
 * @author diabl
 */

public enum TableForeignAction {
    RESTRICT("RESTRICT", "在子表有关联记录的情况下父表不能更新"),
    NO_ACTION("NO ACTION", "在子表有关联记录的情况下父表不能更新"),
    SET_NULL("SET NULL", "父表在更新或者删除的时候，子表的对应字段被SET NULL"),
    CASCADE("CASCADE", "父表在更新或者删除时，更新或者删除子表对应记录");
    private final String code;
    private final String explain;

    TableForeignAction(String code, String explain) {
        this.code = code;
        this.explain = explain;
    }

    public String getCode() {
        return code;
    }

    public String getExplain() {
        return explain;
    }
}
