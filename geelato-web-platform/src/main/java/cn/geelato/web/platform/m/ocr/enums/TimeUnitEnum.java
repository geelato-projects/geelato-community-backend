package cn.geelato.web.platform.m.ocr.enums;

import lombok.Getter;

import java.util.Calendar;

@Getter
public enum TimeUnitEnum {
    YEAR(Calendar.YEAR),
    MONTH(Calendar.MONTH),
    DAY(Calendar.DAY_OF_MONTH),
    HOUR(Calendar.HOUR_OF_DAY),
    MINUTE(Calendar.MINUTE),
    SECOND(Calendar.SECOND);

    private final int value;

    TimeUnitEnum(int value) {
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
