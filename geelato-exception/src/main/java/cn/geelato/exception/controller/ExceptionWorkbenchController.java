package cn.geelato.exception.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.exception.entity.FmsException;
import cn.geelato.exception.service.ExceptionWorkbenchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常工作台 Controller（异常处理人员）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/exception}。
 *
 * <p>端点：pageQuery / summary / domain-stats / {id} / {id}/steps / {id}/handles /
 * {id}/notifies / informs / informs/{id}/read / {id}/claim|note|resolve|ignore|escalate。
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §2
 */
@ApiRuntimeRestController("/exception")
public class ExceptionWorkbenchController {

    @Autowired
    private ExceptionWorkbenchService workbenchService;

    // ==================== 清单 / 看板 ====================

    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) ExceptionWorkbenchService.PageQueryParams params) {
        ExceptionWorkbenchService.PageQueryParams p = params == null
                ? new ExceptionWorkbenchService.PageQueryParams() : params;
        return ApiResult.success(workbenchService.pageQuery(p));
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.success(workbenchService.summary());
    }

    @GetMapping("/domain-stats")
    public ApiResult<List<Map<String, Object>>> domainStats() {
        return ApiResult.success(workbenchService.domainStats());
    }

    // ==================== 详情 / 轨迹 ====================

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) {
        FmsException ex = workbenchService.getById(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        return ApiResult.success(workbenchService.detail(ex));
    }

    @GetMapping("/{id}/steps")
    public ApiResult<List<Map<String, Object>>> steps(@PathVariable String id) {
        return ApiResult.success(workbenchService.steps(id));
    }

    @GetMapping("/{id}/handles")
    public ApiResult<List<Map<String, Object>>> handles(@PathVariable String id) {
        return ApiResult.success(workbenchService.handles(id));
    }

    // ==================== 处置动作（CAS）====================

    @PostMapping("/{id}/claim")
    public ApiResult<Map<String, Object>> claim(@PathVariable String id) {
        FmsException ex = require(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        return outcome(workbenchService.claim(ex));
    }

    @PostMapping("/{id}/note")
    public ApiResult<Map<String, Object>> note(@PathVariable String id, @RequestBody(required = false) NoteRequest req) {
        FmsException ex = require(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        if (req == null || req.getRemark() == null || req.getRemark().isBlank()) {
            return ApiResult.fail(40000, "remark 不能为空");
        }
        return outcome(workbenchService.note(ex, req.getRemark().trim()));
    }

    @PostMapping("/{id}/resolve")
    public ApiResult<Map<String, Object>> resolve(@PathVariable String id,
                                                  @RequestBody(required = false) ResolveRequest req) {
        FmsException ex = require(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        if (req == null || req.getRemark() == null || req.getRemark().isBlank()) {
            return ApiResult.fail(40000, "关闭说明 remark 不能为空");
        }
        return outcome(workbenchService.resolve(ex, req.getRemark().trim()));
    }

    @PostMapping("/{id}/ignore")
    public ApiResult<Map<String, Object>> ignore(@PathVariable String id, @RequestBody IgnoreRequest req) {
        FmsException ex = require(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        if (req == null || req.getReasonCode() == null || req.getReasonCode().isBlank()) {
            return ApiResult.fail(40000, "忽略原因码 reasonCode 不能为空");
        }
        return outcome(workbenchService.ignore(ex, req.getReasonCode().trim(),
                req.getRemark() == null ? null : req.getRemark().trim()));
    }

    @PostMapping("/{id}/escalate")
    public ApiResult<Map<String, Object>> escalate(@PathVariable String id,
                                                   @RequestBody(required = false) NoteRequest req) {
        FmsException ex = require(id);
        if (ex == null) {
            return ApiResult.fail(40400, "实例不存在: " + id);
        }
        return outcome(workbenchService.escalate(ex, req == null ? null : req.getRemark()));
    }

    // ==================== 内部辅助 ====================

    private FmsException require(String id) {
        return workbenchService.getById(id);
    }

    private ApiResult<Map<String, Object>> outcome(ExceptionWorkbenchService.ActionOutcome outcome) {
        if (!outcome.success) {
            return ApiResult.fail(40900, outcome.conflictMessage);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", outcome.status);
        data.put("currentStepCode", outcome.currentStepCode);
        return ApiResult.success(data);
    }

    // ==================== 请求 DTO ====================

    /** 备注 / 升级请求（remark）。 */
    @lombok.Data
    public static class NoteRequest {
        private String remark;
    }

    /** 解决请求（关闭说明）。 */
    @lombok.Data
    public static class ResolveRequest {
        private String remark;
    }

    /** 忽略请求（原因码 + 备注）。 */
    @lombok.Data
    public static class IgnoreRequest {
        private String reasonCode;
        private String remark;
    }
}
