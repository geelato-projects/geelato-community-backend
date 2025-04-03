package cn.geelato.web.platform.m.syspackage.enums;

import lombok.Getter;

/**
 * @author diabl
 * @description: 应用版本状态
 */
@Getter
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
}
