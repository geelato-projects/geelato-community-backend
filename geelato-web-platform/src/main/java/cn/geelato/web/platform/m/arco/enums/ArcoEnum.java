package cn.geelato.web.platform.m.arco.enums;

import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import cn.geelato.web.platform.m.file.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Getter
public enum ArcoEnum {
    RULE_TYPE_ENUM("RuleTypeEnum", RuleTypeEnum.class),
    ATTACHMENT_SERVICE_ENUM("AttachmentServiceEnum", AttachmentServiceEnum.class),
    ATTACHMENT_SOURCE_ENUM("AttachmentSourceEnum", AttachmentSourceEnum.class);

    private final String code;
    private final Class<?> clazz;

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

    public static List<SelectOptionData> getSelectOptions(Class<?> enumClass) {
        List<SelectOptionData> options = new ArrayList<>();
        if (!enumClass.isEnum()) {
            return options;
        }
        Object[] enumConstants = enumClass.getEnumConstants();
        for (Object enumConstant : enumConstants) {
            SelectOptionData option = new SelectOptionData();
            // 设置value (优先尝试getValue()或value字段，否则使用name())
            option.setValue(getEnumValue(enumConstant));
            // 设置label (优先尝试getLabel()或label字段，否则使用name())
            option.setLabel(getEnumLabel(enumConstant));
            // 设置label (优先尝试getLabel()或label字段，否则使用name())
            option.setEnLabel(getEnumEnLabel(enumConstant));
            // 设置other (尝试getDescription()/description或getOther()/other)
            option.setOther(getEnumOther(enumConstant));
            // 设置disabled (默认为false)
            option.setDisabled(getEnumDisabled(enumConstant));
            options.add(option);
        }
        return options;
    }

    private static String getEnumValue(Object enumConstant) {
        return getEnumAttribute(enumConstant,
                new String[]{"value"},
                new String[]{"getValue"},
                ((Enum<?>) enumConstant).name(),
                String.class);
    }

    private static String getEnumLabel(Object enumConstant) {
        return getEnumAttribute(enumConstant,
                new String[]{"label"},
                new String[]{"getLabel"},
                ((Enum<?>) enumConstant).name(),
                String.class);
    }

    private static String getEnumEnLabel(Object enumConstant) {
        return getEnumAttribute(enumConstant,
                new String[]{"enLabel"},
                new String[]{"getEnLabel"},
                ((Enum<?>) enumConstant).name(),
                String.class);
    }

    private static String getEnumOther(Object enumConstant) {
        return getEnumAttribute(enumConstant,
                new String[]{"description", "other", "remark"},
                new String[]{"getDescription", "getOther", "getRemark"},
                null,
                String.class);
    }

    private static boolean getEnumDisabled(Object enumConstant) {
        Boolean result = getEnumAttribute(enumConstant,
                new String[]{"disabled"},
                new String[]{"isDisabled"},
                false,
                Boolean.class);
        return result != null ? result : false;
    }

    /**
     * 反射工具方法 - 统一处理字段和方法调用
     */
    private static <T> T getEnumAttribute(Object enumConstant, String[] fieldNames, String[] methodNames, T defaultValue, Class<T> returnType) {
        // 优先查找字段
        for (String fieldName : fieldNames) {
            try {
                Field field = enumConstant.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(enumConstant);
                if (returnType.isInstance(value)) {
                    return returnType.cast(value);
                }
            } catch (Exception ignored) {
                // 忽略单个字段查找失败
            }
        }
        // 其次查找方法
        for (String methodName : methodNames) {
            try {
                Method method = enumConstant.getClass().getMethod(methodName);
                Object value = method.invoke(enumConstant);
                if (returnType.isInstance(value)) {
                    return returnType.cast(value);
                }
            } catch (Exception ignored) {
                // 忽略单个方法查找失败
            }
        }
        return defaultValue;
    }
}
