package cn.geelato.web.platform.srv.platform.enums;

public enum ApplicationTypeEnum {
    PLATFORM("平台应用", "platform"),
    NORMAL("普通应用", "normal");

    private final String label;// 选项内容
    private final String value;// 选项值

    ApplicationTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
