package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 * 模型字段是否同步
 */
@Getter
public enum ColumnSyncedEnum {
    TRUE("已同步", true),
    FALSE("未同步", false);

    private final String label;// 选项内容
    private final Boolean value;// 选项值

    ColumnSyncedEnum(String label, Boolean value) {
        this.label = label;
        this.value = value;
    }
}
