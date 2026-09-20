package cn.geelato.web.platform.srv.ormhook.spi;

import lombok.Getter;

/**
 * 钩子动作执行结果（对齐通知渠道 {@code ChannelResult} 模式）。
 *
 * @author geelato
 */
@Getter
public class OrmHookActionResult {

    private final boolean success;
    private final String errorMessage;

    private OrmHookActionResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static OrmHookActionResult success() {
        return new OrmHookActionResult(true, null);
    }

    public static OrmHookActionResult fail(String errorMessage) {
        return new OrmHookActionResult(false, errorMessage == null || errorMessage.isBlank()
                ? "unknown error" : errorMessage);
    }
}
