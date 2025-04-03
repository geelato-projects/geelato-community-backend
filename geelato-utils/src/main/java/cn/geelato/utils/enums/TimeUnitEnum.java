package cn.geelato.utils.enums;

import lombok.Getter;

import java.util.Calendar;

@Getter
public enum TimeUnitEnum {
    YEAR("年", Calendar.YEAR),
    MONTH("月", Calendar.MONTH),
    DAY("日", Calendar.DAY_OF_MONTH),
    HOUR("时", Calendar.HOUR_OF_DAY),
    MINUTE("分", Calendar.MINUTE),
    SECOND("秒", Calendar.SECOND);

    private final String label;
    private final int value;

    TimeUnitEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }

    public static int getValueByName(String name) {
        for (TimeUnitEnum unit : values()) {
            if (unit.name().equalsIgnoreCase(name)) {
                return unit.getValue();
            }
        }
        return -1;
    }
}
