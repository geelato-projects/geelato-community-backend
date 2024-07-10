package cn.geelato.web.platform.enums;

import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 * @description: 应用版本状态
 * @date 2024/6/18 14:31
 */
public enum PackageStatusEnum {
    DRAFT("未发布", "draft"),
    RELEASE("已发布", "release"),
    UNUSED("未使用", "unused"),
    DEPLOYED("已使用", "deployed");

    private final String label;// 选项内容
    private final String value;// 选项值

    PackageStatusEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (PackageStatusEnum enums : PackageStatusEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}