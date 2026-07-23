package cn.geelato.web.platform.run;

import cn.geelato.core.GlobalContext;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.ErrorCode;
import cn.geelato.utils.BeanValidators;
import cn.geelato.utils.UIDGenerator;
import com.alibaba.fastjson.JSON;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;


/**
 * 自定义ExceptionHandler，专门处理Restful异常.
 **/
@RestControllerAdvice
@Slf4j
public class PlatformExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorDocResolver errorDocResolver = new ErrorDocResolver();

    @org.springframework.web.bind.annotation.ExceptionHandler(value = {ConstraintViolationException.class})
    public final ResponseEntity<?> handleException(ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = BeanValidators.extractPropertyAndMessage(ex.getConstraintViolations());
        String body = JSON.toJSONString(errors);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.TEXT_PLAIN_UTF_8));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }


    @org.springframework.web.bind.annotation.ExceptionHandler(value = {CoreException.class})
    public final ResponseEntity<?> handleException(CoreException ex, WebRequest request) {
        PlatformErrorResult errorResult = coreException2PlatformErrorResult(ex);
        ApiResult<PlatformErrorResult> apiResult = ApiResult.fail(errorResult,
                ex.getErrorMsg() != null ? ex.getErrorMsg() : "系统异常");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        // 按 ErrorCode 声明的 HTTP 状态码返回（鉴权类 401/403/400，其余默认 500）
        HttpStatus httpStatus = HttpStatus.resolve(resolveHttpStatus(ex));
        return handleExceptionInternal(ex, apiResult, headers, httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private PlatformErrorResult coreException2PlatformErrorResult(CoreException coreException) {
        PlatformErrorResult errorResult = new PlatformErrorResult(coreException);
        String logTag = Long.toString(UIDGenerator.generate());
        String logMessage = "logTag=" + logTag + "|userId=" + errorResult.getOccurUserId() + "|occurTime=" + errorResult.getOccurTime();
        log.error(logMessage, coreException);
        errorResult.setLogTag(logTag);
        errorResult.setDocUrl(errorDocResolver.resolve(coreException));
        if (!GlobalContext.getLogStack()) {
            errorResult.setCoreException(null);
        }
        return errorResult;
    }

    /**
     * 从 {@link ErrorCode} 读取声明的 HTTP 状态码；异常未持有 ErrorCode 时回退到 500。
     */
    private int resolveHttpStatus(CoreException ex) {
        ErrorCode ec = ex.getError();
        if (ec == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        int status = ec.getHttpStatus();
        // 仅放行标准可用的 4xx/5xx 状态码，避免异常类误声明非法值导致 ResponseEntity 构造失败
        if (status >= 400 && status <= 599) {
            return status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    /**
     * 处理除以上问题之后的其它问题
     */
    @org.springframework.web.bind.annotation.ExceptionHandler
    public final ResponseEntity<?> handleOtherException(Exception ex, WebRequest request) {
        // 兜底异常此前只包装返回、不打日志，导致“前端能看到错误、服务端控制台却无任何记录”，无法定位。
        // 这里统一记录堆栈；请求描述由 WebRequest 提供（如 uri=/xxx;client=ip）。
        log.error("Unhandled exception on [{}]: {}", request.getDescription(false), ex.getMessage(), ex);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        ApiResult<Object> apiResult = ApiResult.fail(ex.getMessage() != null ? ex.getMessage() : "系统异常");
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.BAD_REQUEST, request);
    }
}
