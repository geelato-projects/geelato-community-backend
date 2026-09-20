package cn.geelato.web.platform.srv.ormhook.enums;

/**
 * ORM 钩子执行发件箱状态（对齐通知 outbox：fail 不落库，与 ready 合并存储为 ready+next_retry_at）。
 *
 * @author geelato
 */
public enum HookLogStatusEnum {

    READY("ready", "就绪待执行"),

    PROCESSING("processing", "执行中（已 CAS 抢占）"),

    SUCCESS("success", "执行成功"),

    DEAD("dead", "死信（达最大重试次数，可手动重放）");

    private final String value;
    private final String title;

    HookLogStatusEnum(String value, String title) {
        this.value = value;
        this.title = title;
    }

    public String value() {
        return value;
    }

    public String title() {
        return title;
    }
}
