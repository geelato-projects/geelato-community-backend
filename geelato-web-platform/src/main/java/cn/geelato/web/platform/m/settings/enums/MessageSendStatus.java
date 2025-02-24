package cn.geelato.web.platform.m.settings.enums;

import cn.geelato.web.platform.m.security.enums.EncodingItemTypeEnum;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

@Getter
public enum MessageSendStatus {
    UNSENT("未发送", 0),
    SUCCESS("发送成功", 1),
    FAIL("发送失败", 2),
    READ("已读", 3);

    private final String label;// 选项内容
    private final int value;// 选项值

    MessageSendStatus(String label, int value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (EncodingItemTypeEnum enums : EncodingItemTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}
