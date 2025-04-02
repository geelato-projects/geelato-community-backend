package cn.geelato.web.platform.m.arco.enums;

import cn.geelato.core.enums.*;
import cn.geelato.utils.enums.LocaleEnum;
import cn.geelato.utils.enums.TimeUnitEnum;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.enums.RestfulConfigType;
import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import cn.geelato.web.platform.m.base.enums.*;
import cn.geelato.web.platform.m.excel.enums.*;
import cn.geelato.web.platform.m.file.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.ocr.enums.DictDisposeEnum;
import cn.geelato.web.platform.m.ocr.enums.MetaTypeEnum;
import cn.geelato.web.platform.m.ocr.enums.RuleTypeEnum;
import cn.geelato.web.platform.m.script.enums.AlternateTypeEnum;
import cn.geelato.web.platform.m.security.enums.*;
import cn.geelato.web.platform.m.settings.enums.MessageSendStatus;
import cn.geelato.web.platform.m.syspackage.enums.PackageSourceEnum;
import cn.geelato.web.platform.m.syspackage.enums.PackageStatusEnum;
import cn.geelato.web.platform.m.zxing.enums.*;
import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Getter
public enum ArcoEnum {
    // 根包枚举
    PERMISSION_TYPE_ENUM("PermissionTypeEnum", PermissionTypeEnum.class),
    RESTFUL_CONFIG_TYPE("RestfulConfigType", RestfulConfigType.class),
    // 基础相关枚举
    SYS_CONFIG_VALUE_TYPE_ENUM("SysConfigValueTypeEnum", SysConfigValueTypeEnum.class),
    SYS_CONFIG_PURPOSE_ENUM("SysConfigPurposeEnum", SysConfigPurposeEnum.class),
    SCHEDULE_TYPE_ENUM("ScheduleTypeEnum", ScheduleTypeEnum.class),
    SCHEDULE_LOG_TYPE_ENUM("ScheduleLogTypeEnum", ScheduleLogTypeEnum.class),
    SCHEDULE_LOG_STATUS_ENUM("ScheduleLogStatusEnum", ScheduleLogStatusEnum.class),
    RESPONSE_PARAM_TYPE_ENUM("ResponseParamTypeEnum", ResponseParamTypeEnum.class),
    MODEL_COLUMN_PCL_ENUM("ModelColumnPclEnum", ModelColumnPclEnum.class),
    APPROVAL_STATUS_ENUM("ApprovalStatusEnum", ApprovalStatusEnum.class),
    // Excel相关枚举
    EXCEL_ALIGNMENT_ENUM("ExcelAlignmentEnum", ExcelAlignmentEnum.class),
    EXCEL_MULTI_SCENE_TYPE_ENUM("ExcelMultiSceneTypeEnum", ExcelMultiSceneTypeEnum.class),
    WORD_TABLE_LOOP_TYPE_ENUM("WordTableLoopTypeEnum", WordTableLoopTypeEnum.class),
    EXCEL_COLUMN_TYPE_RULE_ENUM("ExcelColumnTypeRuleEnum", ExcelColumnTypeRuleEnum.class),
    EXCEL_EVALUATION_ENUM("ExcelEvaluationEnum", ExcelEvaluationEnum.class),
    EXCEL_COLUMN_TYPE_ENUM("ExcelColumnTypeEnum", ExcelColumnTypeEnum.class),
    // 文件相关枚举
    ATTACHMENT_SERVICE_ENUM("AttachmentServiceEnum", AttachmentServiceEnum.class),
    ATTACHMENT_SOURCE_ENUM("AttachmentSourceEnum", AttachmentSourceEnum.class),
    // OCR相关枚举
    RULE_TYPE_ENUM("RuleTypeEnum", RuleTypeEnum.class),
    META_TYPE_ENUM("MetaTypeEnum", MetaTypeEnum.class),
    DICT_DISPOSE_ENUM("DictDisposeEnum", DictDisposeEnum.class),
    // 脚本相关枚举
    SCRIPT_RESPONSE_PARAM_TYPE_ENUM("ScriptResponseParamTypeEnum", ResponseParamTypeEnum.class),
    ALTERNATE_TYPE_ENUM("AlternateTypeEnum", AlternateTypeEnum.class),
    // 安全相关枚举
    ROLE_TYPE_ENUM("RoleTypeEnum", RoleTypeEnum.class),
    VALID_TYPE_ENUM("ValidTypeEnum", ValidTypeEnum.class),
    ORG_TYPE_ENUM("OrgTypeEnum", OrgTypeEnum.class),
    IS_DEFAULT_ORG_ENUM("IsDefaultOrgEnum", IsDefaultOrgEnum.class),
    ENCODING_SERIAL_TYPE_ENUM("EncodingSerialTypeEnum", EncodingSerialTypeEnum.class),
    ENCODING_ITEM_TYPE_ENUM("EncodingItemTypeEnum", EncodingItemTypeEnum.class),
    AUTH_CODE_ACTION("AuthCodeAction", AuthCodeAction.class),
    // 设置相关枚举
    MESSAGE_SEND_STATUS("MessageSendStatus", MessageSendStatus.class),
    // 系统包相关枚举
    PACKAGE_STATUS_ENUM("PackageStatusEnum", PackageStatusEnum.class),
    PACKAGE_SOURCE_ENUM("PackageSourceEnum", PackageSourceEnum.class),
    // 条码相关枚举
    BARCODE_TYPE_ENUM("BarcodeTypeEnum", BarcodeTypeEnum.class),
    BARCODE_SIZE_UNIT_ENUM("BarcodeSizeUnitEnum", BarcodeSizeUnitEnum.class),
    BARCODE_PICTURE_FORMAT_ENUM("BarcodePictureFormatEnum", BarcodePictureFormatEnum.class),
    BARCODE_FONT_STYLE_ENUM("BarcodeFontStyleEnum", BarcodeFontStyleEnum.class),
    BARCODE_FONT_POSITION_ENUM("BarcodeFontPositionEnum", BarcodeFontPositionEnum.class),
    BARCODE_FONT_ALIGN_ENUM("BarcodeFontAlignEnum", BarcodeFontAlignEnum.class),
    // Core相关枚举
    VIEW_TYPE_ENUM("ViewTypeEnum", ViewTypeEnum.class),
    MYSQL_DATA_TYPE_ENUM("MysqlDataTypeEnum", MysqlDataTypeEnum.class),
    TABLE_SOURCE_TYPE_ENUM("TableSourceTypeEnum", TableSourceTypeEnum.class),
    TABLE_TYPE_ENUM("TableTypeEnum", TableTypeEnum.class),
    TABLE_FOREIGN_ACTION("TableForeignAction", TableForeignAction.class),
    LINKED_ENUM("LinkedEnum", LinkedEnum.class),
    ENABLE_STATUS_ENUM("EnableStatusEnum", EnableStatusEnum.class),
    DIALECTS("Dialects", Dialects.class),
    DELETE_STATUS_ENUM("DeleteStatusEnum", DeleteStatusEnum.class),
    COLUMN_SYNCED_ENUM("ColumnSyncedEnum", ColumnSyncedEnum.class),
    COLUMN_ENCRYPTED_ENUM("ColumnEncryptedEnum", ColumnEncryptedEnum.class),
    // Utils相关枚举
    TIME_UNIT_ENUM("TimeUnitEnum", TimeUnitEnum.class),
    LOCALE_ENUM("LocaleEnum", LocaleEnum.class);

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
