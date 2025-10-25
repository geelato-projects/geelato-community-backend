package cn.geelato.web.platform.srv.settings.enums;

import lombok.Getter;

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
}
