package cn.geelato.alert.engine.deliver;

/**
 * 投递失败（message 调用异常/渠道映射缺失/渲染失败等），message 恒不抛 checked。
 * getMessage 恒非空（含响应摘要），供 record.error_message（varchar(512)，调用方截断）。
 */
public class DeliverException extends RuntimeException {

    public DeliverException(String message) {
        super(message == null || message.isBlank() ? "投递失败（未提供原因）" : message);
    }

    public DeliverException(String message, Throwable cause) {
        super(message == null || message.isBlank() ? "投递失败（未提供原因）" : message, cause);
    }

    /** 错误信息截断至 maxLen（含省略号标记），落 error_message 列前调用。 */
    public String truncated(int maxLen) {
        String msg = getMessage();
        return msg.length() <= maxLen ? msg : msg.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
