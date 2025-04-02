package cn.geelato.web.platform.m.file.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 */
@Getter
public enum AttachmentSourceEnum {
    ATTACH("附属文件", "attach"),
    COMPRESS("压缩文件", "compress"),
    RESOURCES("资源文件", "resources");

    private final String label;// 选项内容
    private final String value;// 选项值

    AttachmentSourceEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (AttachmentSourceEnum enums : AttachmentSourceEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }

    /**
     * 根据给定的值获取对应的枚举类。
     *
     * @param value 要获取的枚举类对应的值
     * @return 返回对应的枚举类对象，如果未找到对应的枚举类，则返回null
     */
    public static AttachmentSourceEnum getEnum(String value) {
        if (Strings.isNotBlank(value)) {
            for (AttachmentSourceEnum enums : AttachmentSourceEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums;
                }
            }
        }
        return null;
    }
}
