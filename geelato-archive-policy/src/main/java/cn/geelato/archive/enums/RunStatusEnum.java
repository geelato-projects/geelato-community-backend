package cn.geelato.archive.enums;

/** 归档运行状态。复杂状态全部在 run 侧，策略仅有启用/停用两态。 */
public enum RunStatusEnum {

    RUNNING("运行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELED("已中止");

    private final String description;

    RunStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 终态判定（可发起新 run 的前提） */
    public static boolean isTerminal(String status) {
        return status == null || !RUNNING.name().equalsIgnoreCase(status);
    }
}
