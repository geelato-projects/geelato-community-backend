package cn.geelato.web.platform.run;

import cn.geelato.core.GlobalContext;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.exception.CoreException;
import cn.geelato.utils.BeanValidators;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.meta.PlatformExceptionLog;
import cn.geelato.security.App;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.web.platform.errorlog.service.ExceptionLogService;
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

    /**
     * 兜底系统错误码与文案（50001 / 系统繁忙）：未归类异常统一返回，无对应异常类，登记于官方文档错误码参考页。
     */
    private static final int SYSTEM_BUSY_CODE = 50001;
    private static final String SYSTEM_BUSY_MESSAGE = "系统繁忙，请稍后重试";

    private final ErrorDocResolver errorDocResolver = new ErrorDocResolver();
    private final ExceptionLogService exceptionLogService;

    public PlatformExceptionHandler(ExceptionLogService exceptionLogService) {
        this.exceptionLogService = exceptionLogService;
    }

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
        PlatformErrorResult errorResult = coreException2PlatformErrorResult(ex, request);
        // 前端仅见友好文案（getUserMessage，不含 SQL/参数/堆栈等技术详情），末尾追加错误码与反馈凭据，
        // 用户报障时凭截图即可在服务端日志中检索 logTag 对应的完整技术详情。
        String userMessage = ex.getUserMessage() != null ? ex.getUserMessage() : "系统异常";
        ApiResult<PlatformErrorResult> apiResult = ApiResult.fail(errorResult,
                buildFeedbackMessage(userMessage, errorResult));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        // 按 ErrorCode 声明的 HTTP 状态码返回（鉴权类 401/403/400，其余默认 500）
        HttpStatus httpStatus = HttpStatus.resolve(resolveHttpStatus(ex));
        return handleExceptionInternal(ex, apiResult, headers, httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * 在友好文案末尾追加排障凭据（错误码 + logTag），格式如：
     * {@code 数据操作失败，请稍后重试（错误码 10010，反馈凭据 123456789）}。
     */
    private String buildFeedbackMessage(String userMessage, PlatformErrorResult errorResult) {
        return userMessage + "（错误码 " + errorResult.getErrorCode() + "，反馈凭据 " + errorResult.getLogTag() + "）";
    }

    private PlatformErrorResult coreException2PlatformErrorResult(CoreException coreException, WebRequest request) {
        PlatformErrorResult errorResult = new PlatformErrorResult(coreException);
        String logTag = Long.toString(UIDGenerator.generate());
        String logMessage = "logTag=" + logTag + "|userId=" + errorResult.getOccurUserId() + "|occurTime=" + errorResult.getOccurTime();
        log.error(logMessage, coreException);
        errorResult.setLogTag(logTag);
        errorResult.setDocUrl(errorDocResolver.resolve(coreException));
        // LogStack（默认开启）仅控制 stackTraceDetail（技术详情+堆栈）是否下发；msg/errorMsg 恒为友好文案
        if (!GlobalContext.getLogStack()) {
            errorResult.setException(null);
        }
        recordExceptionLog(logTag, coreException, errorResult, request);
        return errorResult;
    }

    /**
     * 异常持久化到 platform_exception_log（id=logTag，异步落库，运维凭反馈凭据查询）。
     * 落库不受前端 LogStack 开关影响（运维数据始终全量）；本方法自身兜底，绝不影响响应返回。
     */
    private void recordExceptionLog(String logTag, Throwable ex, PlatformErrorResult errorResult, WebRequest request) {
        try {
            User user = SecurityContext.getCurrentUser();
            String userId = user != null ? user.getUserId() : null;
            String actorId = userId != null ? userId : "system";
            Tenant tenant = SecurityContext.getCurrentTenant();
            String tenantCode = user != null && user.getTenantCode() != null ? user.getTenantCode()
                    : tenant != null ? tenant.getCode() : null;
            App app = SecurityContext.getCurrentApp();
            PlatformExceptionLog record = new PlatformExceptionLog();
            record.setId(logTag);
            record.setHappenedTime(new java.util.Date());
            record.setExceptionClass(ex.getClass().getName());
            record.setExceptionCode(String.valueOf(errorResult.getErrorCode()));
            record.setExceptionStacktrace("logTag=" + logTag
                    + "|userId=" + userId
                    + "|tenantCode=" + tenantCode
                    + "|uri=" + (request != null ? request.getDescription(false) : "")
                    + "|occurTime=" + errorResult.getOccurTime() + "\n"
                    + PlatformErrorResult.buildStackTraceDetail(ex));
            record.setAppId(app != null ? app.getId() : null);
            record.setTenantCode(tenantCode);
            record.setCreator(actorId);
            record.setUpdater(actorId);
            record.setCreatorName(user != null ? user.getUserName() : null);
            record.setUpdaterName(user != null ? user.getUserName() : null);
            exceptionLogService.record(record);
        } catch (Exception logEx) {
            log.warn("错误日志记录构建失败: logTag={}", logTag, logEx);
        }
    }

    /**
     * 从异常读取声明的 HTTP 状态码（{@link CoreException#getHttpStatus()} 默认 500，鉴权类覆写为 401/403/400）。
     */
    private int resolveHttpStatus(CoreException ex) {
        int status = ex.getHttpStatus();
        // 仅放行标准可用的 4xx/5xx 状态码，避免子类误覆写非法值导致 ResponseEntity 构造失败
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
        // 兜底异常：补生成 logTag 并记录完整堆栈（前端能看到错误、服务端有据可查）；
        // 前端仅返回友好分类文案 + 反馈凭据，不再透出原始 ex.getMessage()。
        // HTTP 状态保持 400（历史行为，避免影响前端按状态码的分支处理）。
        String logTag = Long.toString(UIDGenerator.generate());
        log.error("logTag=" + logTag + "|Unhandled exception on [{}]: {}", request.getDescription(false), ex.getMessage(), ex);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MediaTypes.APPLICATION_JSON_UTF_8));
        PlatformErrorResult errorResult = new PlatformErrorResult(SYSTEM_BUSY_CODE, SYSTEM_BUSY_MESSAGE);
        errorResult.setException(ex);
        errorResult.setLogTag(logTag);
        // 兜底异常同样受 LogStack 控制：关闭时不下发堆栈，msg 仍透出详细错误消息
        if (!GlobalContext.getLogStack()) {
            errorResult.setException(null);
        }
        recordExceptionLog(logTag, ex, errorResult, request);
        ApiResult<PlatformErrorResult> apiResult = ApiResult.fail(errorResult,
                buildFeedbackMessage(fallbackUserMessage(ex), errorResult));
        return handleExceptionInternal(ex, apiResult, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 兜底异常的用户文案：详细消息优先——直接透出异常消息便于排障与自助处理；
     * 无消息的异常（NPE 等）用"系统异常：类名"提供定位线索。
     */
    private String fallbackUserMessage(Exception ex) {
        if (ex.getMessage() != null) {
            return ex.getMessage();
        }
        return "系统异常：" + ex.getClass().getSimpleName();
    }
}
