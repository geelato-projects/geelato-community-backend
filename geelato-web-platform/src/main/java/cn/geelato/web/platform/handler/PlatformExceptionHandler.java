package cn.geelato.web.platform.handler;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.UnSupportedVersionException;
import cn.geelato.plugin.UnFoundPluginException;
import cn.geelato.web.platform.PlatformRuntimeException;
import cn.geelato.web.platform.m.base.rest.RestException;
import com.alibaba.fastjson.JSON;
import jakarta.validation.ConstraintViolationException;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiResultStatus;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.utils.BeanValidators;
import cn.geelato.utils.UIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;


/**
 * 自定义ExceptionHandler，专门处理Restful异常.
 **/
// 会被Spring-MVC自动扫描，但又不属于Controller的annotation。
@ControllerAdvice
public class PlatformExceptionHandler extends ResponseEntityExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(PlatformExceptionHandler.class);
    /**
     * 处理JSR311 Validation异常.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(value = {ConstraintViolationException.class})
    public final ResponseEntity<?> handleException(ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = BeanValidators.extractPropertyAndMessage(ex.getConstraintViolations());
        String body = JSON.toJSONString(errors);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.TEXT_PLAIN_UTF_8));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 处理RestException.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(value = {RestException.class})
    public final ResponseEntity<?> handleException(RestException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.TEXT_PLAIN_UTF_8));
        return handleExceptionInternal(ex, ex.getMessage(), headers, ex.status, request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(value = {CoreException.class})
    public final ResponseEntity<?> handleException(CoreException ex, WebRequest request) {
        ApiResult<PlatformRuntimeException> apiResult=new ApiResult<>();
        apiResult.setCode(ex.getErrorCode());
        apiResult.setMsg(ex.getErrorMsg());
        apiResult.setStatus(ApiResultStatus.FAIL);
        apiResult.setData(coreException2PlatformException(ex));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.JSON_UTF_8));
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private PlatformRuntimeException coreException2PlatformException(CoreException coreException) {
        PlatformRuntimeException platformRuntimeException=new PlatformRuntimeException(coreException);
        String logTag=Long.toString(UIDGenerator.generate());
        logger.error(logTag,coreException);
        platformRuntimeException.setLogTag(logTag);
        return platformRuntimeException;
    }
    @org.springframework.web.bind.annotation.ExceptionHandler(value = {UnFoundPluginException.class})
    public final ResponseEntity<?> handleException(UnFoundPluginException ex, WebRequest request) {
        ApiResult<UnSupportedVersionException> apiResult=new ApiResult<>();
        UnSupportedVersionException unSupportedVersionException=new UnSupportedVersionException();
        apiResult.setCode(unSupportedVersionException.getErrorCode());
        apiResult.setMsg(unSupportedVersionException.getErrorMsg());
        apiResult.setStatus(ApiResultStatus.FAIL);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.JSON_UTF_8));
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
    /**
     * 处理除以上问题之后的其它问题
     */
    @org.springframework.web.bind.annotation.ExceptionHandler
    public final ResponseEntity<?> handleOtherException(Exception ex, WebRequest request) {
        logger.error("Exception", ex);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.TEXT_PLAIN_UTF_8));
        return handleExceptionInternal(ex, ex.getMessage(), headers, HttpStatus.BAD_REQUEST, request);
    }
}
