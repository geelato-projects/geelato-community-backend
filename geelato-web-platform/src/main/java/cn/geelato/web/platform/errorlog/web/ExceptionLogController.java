package cn.geelato.web.platform.errorlog.web;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.PlatformExceptionLog;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.IgnoreSrvLog;
import cn.geelato.web.platform.errorlog.service.ExceptionLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 平台错误日志查询接口（面向运维/管理员）。
 *
 * <p>用户报障时提供响应中的反馈凭据（logTag），运维凭
 * {@code GET /api/exceptionLog/byTag/{logTag}} 精确取到该次异常的完整详情
 * （结构化前缀 + 技术消息 + 堆栈），无需登服务器检索日志文件。</p>
 */
@IgnoreSrvLog
@ApiRestController("/exceptionLog")
public class ExceptionLogController {

    private final ExceptionLogService exceptionLogService;

    public ExceptionLogController(ExceptionLogService exceptionLogService) {
        this.exceptionLogService = exceptionLogService;
    }

    /**
     * 按反馈凭据（=主键）精确查询，含完整堆栈。
     */
    @GetMapping("/byTag/{logTag}")
    public ApiResult<PlatformExceptionLog> byTag(@PathVariable("logTag") String logTag) {
        if (!StringUtils.hasText(logTag)) {
            return ApiResult.fail("logTag 不能为空");
        }
        PlatformExceptionLog record = exceptionLogService.findByTag(logTag);
        if (record == null) {
            return ApiResult.fail("错误日志不存在（可能落库失败已降级写文件，请在服务端 errorlog 降级日志中检索 logTag）");
        }
        return ApiResult.success(record);
    }

    /**
     * 分页查询错误日志（列表剥除堆栈大字段降低传输，详情走 byTag）。
     */
    @GetMapping("/page")
    public ApiPagedResult<PlatformExceptionLog> page(
            @RequestParam(value = "exceptionCode", required = false) String exceptionCode,
            @RequestParam(value = "appId", required = false) String appId,
            @RequestParam(value = "tenantCode", required = false) String tenantCode,
            @RequestParam(value = "fromTime", required = false) Long fromTime,
            @RequestParam(value = "toTime", required = false) Long toTime,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        ApiPagedResult<PlatformExceptionLog> result = exceptionLogService.page(
                exceptionCode, appId, tenantCode, fromTime, toTime, pageNum, pageSize);
        stripStackTrace(result);
        return result;
    }

    /** 剥除列表中的 exceptionStacktrace（大字段）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stripStackTrace(ApiPagedResult result) {
        Object data = result.getData();
        if (data instanceof List list) {
            for (Object item : list) {
                if (item instanceof PlatformExceptionLog record) {
                    record.setExceptionStacktrace(null);
                }
            }
        }
    }
}
