package cn.geelato.exception.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.exception.service.ExceptionAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 异常事件接入 / 管理端点 Controller。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/exception}。
 *
 * <p>端点：entity-event（写入宿主 → FMS，SystemToken 鉴权由平台拦截器处理）、
 * admin/scan、admin/scan-logs、admin/archive。
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §3、§5
 */
@ApiRuntimeRestController("/exception")
public class ExceptionAdminController {

    @Autowired
    private ExceptionAdminService adminService;

    /**
     * 实体变更事件接入（跨宿主 Hook）。鉴权由平台 SystemToken 拦截器处理。
     *
     * <p>幂等：{@code eventId} 去重；复评/建实例引擎留后续，端点恒返回 200（失败走 Outbox 补偿）。
     */
    @PostMapping("/entity-event")
    public ApiResult<Map<String, Object>> entityEvent(
            @RequestBody ExceptionAdminService.EntityEventRequest req) {
        try {
            return ApiResult.success(adminService.entityEvent(req));
        } catch (IllegalArgumentException e) {
            // 事件接入对上游恒 200，入参非法也仅记录不阻断（避免推送端重试风暴）
            return ApiResult.success(Map.of("accepted", false, "reason", e.getMessage()));
        }
    }

    /** 手动触发扫描（占位留痕，不执行管道）。 */
    @PostMapping("/admin/scan")
    public ApiResult<Map<String, Object>> scan(@RequestBody(required = false) ExceptionAdminService.ScanRequest req) {
        return ApiResult.success(adminService.adminScan(req));
    }

    /** 扫描日志查询（治理监控）。 */
    @GetMapping("/admin/scan-logs")
    public ApiResult<Map<String, Object>> scanLogs(@RequestParam(required = false) String ruleCode,
                                                   @RequestParam(required = false, defaultValue = "1") int pageNum,
                                                   @RequestParam(required = false, defaultValue = "20") int pageSize) {
        ExceptionAdminService.ScanLogQuery query = new ExceptionAdminService.ScanLogQuery();
        query.ruleCode = ruleCode;
        query.pageNum = pageNum;
        query.pageSize = pageSize;
        return ApiResult.success(adminService.scanLogs(query));
    }

    /** 归档触发（契约面统计，搬迁引擎留后续）。 */
    @PostMapping("/admin/archive")
    public ApiResult<Map<String, Object>> archive(
            @RequestBody(required = false) ExceptionAdminService.ArchiveRequest req) {
        return ApiResult.success(adminService.archive(req));
    }
}
