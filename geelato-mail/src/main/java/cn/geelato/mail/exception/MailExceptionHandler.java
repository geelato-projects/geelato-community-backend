package cn.geelato.mail.exception;

import cn.geelato.lang.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 邮件模块全局异常处理器。
 *
 * <p>统一拦截邮件模块业务异常，转换为前端可消费的 {@link ApiResult} 结构
 * （status="fail"），与前端 runtime 响应拦截器对齐。</p>
 *
 * <p><b>范围限制</b>：仅处理 {@code cn.geelato.mail} 包内抛出的异常，
 * 避免拦截宿主应用其他端点的异常导致错误被掩盖。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "cn.geelato.mail")
public class MailExceptionHandler {

    /**
     * 会话/访问异常：携带业务码 40903，记 WARN 后返回。
     */
    @ExceptionHandler(MailAccessException.class)
    public ApiResult<Void> handleMailAccess(MailAccessException e) {
        log.warn("邮件模块访问异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }
}
