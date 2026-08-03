package cn.geelato.web.platform.srv.notification.enums;

/**
 * 投递发件箱状态机。
 * <pre>
 * ready ──claim(CAS)──▶ processing ──ok──▶ success
 *                            │
 *                            └──fail──▶ retry_wait(指数退避)──▶ ready
 *                                            │
 *                            达上限/永久失败 └▶ dead（死信）
 * </pre>
 *
 * @author geelato
 */
public enum OutboxStatusEnum {

    READY("ready"),
    PROCESSING("processing"),
    SUCCESS("success"),
    /** 失败待重试：与 ready 合并存储为 ready + next_retry_at，扫描时只挑 next_retry_at 到期者 */
    FAIL("fail"),
    DEAD("dead");

    private final String value;

    OutboxStatusEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
