package cn.geelato.web.platform.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 */
@Getter
public enum PermissionTypeEnum {
    DATA("数据权限", "dp"),
    ELEMENT("页面元素权限", "ep"),
    MODEL("实体模型权限", "mp"),
    COLUMN("实体字段权限", "cp");

    private final String label;//选项内容
    private final String value;//选项值

    PermissionTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (PermissionTypeEnum enums : PermissionTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }

    /**
     * 获取模型拥有的权限
     *
     * @return
     */
    public static String getTablePermissions() {
        return String.format("%s,%s", PermissionTypeEnum.DATA.getValue(), PermissionTypeEnum.MODEL.getValue());
    }

    /**
     * 获取模型权限+字段权限
     *
     * @return
     */
    public static String getTableAndColumnPermissions() {
        return String.format("%s,%s,%s", PermissionTypeEnum.DATA.getValue(), PermissionTypeEnum.MODEL.getValue(), PermissionTypeEnum.COLUMN.getValue());
    }
}