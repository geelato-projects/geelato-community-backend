package cn.geelato.web.platform.handler;

import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.UnSupportedVersionException;
import cn.geelato.plugin.UnFoundPluginException;
import cn.geelato.utils.BeanValidators;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.PlatformRuntimeException;
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
        ApiResult<PlatformRuntimeException> apiResult = ApiResult.fail(coreException2PlatformException(ex), ex.getErrorMsg());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private PlatformRuntimeException coreException2PlatformException(CoreException coreException) {
        PlatformRuntimeException platformRuntimeException = new PlatformRuntimeException(coreException);
        String logTag = Long.toString(UIDGenerator.generate());
        log.error(logTag, coreException);
        platformRuntimeException.setLogTag(logTag);
        return platformRuntimeException;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(value = {UnFoundPluginException.class})
    public final ResponseEntity<?> handleException(UnFoundPluginException ex, WebRequest request) {
        UnSupportedVersionException unSupportedVersionException = new UnSupportedVersionException();
        ApiResult<UnSupportedVersionException> apiResult = ApiResult.fail(unSupportedVersionException.getErrorCode(), unSupportedVersionException.getErrorMsg());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * 处理除以上问题之后的其它问题
     */
    @org.springframework.web.bind.annotation.ExceptionHandler
    public final ResponseEntity<?> handleOtherException(Exception ex, WebRequest request) {
        log.error("Exception", ex);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.TEXT_PLAIN_UTF_8));
        return handleExceptionInternal(ex, ex.getMessage(), headers, HttpStatus.BAD_REQUEST, request);
    }
}
