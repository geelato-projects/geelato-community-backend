package cn.geelato.mail.exception;

import cn.geelato.lang.api.ApiResult;
import lombok.Getter;

/**
 * 邮件模块访问/会话异常（code=40903，与 SWF 权限码对齐）。
 *
 * 当邮件 Service 层无法解析当前登录用户（未登录或会话过期）时抛出，
 * 由 {@link MailExceptionHandler} 统一转换为 {@link ApiResult}。
 */
@Getter
public class MailAccessException extends RuntimeException {

    public static final int CODE = 40903;

    private final int code;

    public MailAccessException(String message) {
        super(message);
        this.code = CODE;
    }
}
