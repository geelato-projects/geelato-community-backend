package cn.geelato.web.platform.enums;

import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 * @description: 应用版本来源
 * @date 2024/6/18 14:28
 */
public enum PackageSourceEnum {
    PACKET("当前环境打包", "packet"),
    UPLOAD("版本包上传", "upload"),
    SYNC("版本仓库下载", "sync");

    private final String label;// 选项内容
    private final String value;// 选项值

    PackageSourceEnum(String label, String value) {
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
            for (PackageSourceEnum enums : PackageSourceEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}