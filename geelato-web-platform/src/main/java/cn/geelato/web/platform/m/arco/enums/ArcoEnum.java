package cn.geelato.web.platform.m.arco.enums;

import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import lombok.Getter;

@Getter
public enum ArcoEnum {
    RULE_TYPE_ENUM("RuleTypeEnum", RuleTypeEnum.class);


    private String code;
    private Class<?> clazz;

    ArcoEnum(String code, Class<?> clazz) {
        this.code = code;
        this.clazz = clazz;
    }

    public static Class<?> getClassByCode(String code) {
        for (ArcoEnum arcoEnum : ArcoEnum.values()) {
            if (arcoEnum.getCode().equalsIgnoreCase(code)) {
                return arcoEnum.getClazz();
            }
        }
        return null;
    }
}
