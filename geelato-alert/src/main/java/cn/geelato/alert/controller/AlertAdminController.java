package cn.geelato.alert.controller;

import cn.geelato.alert.service.AlertAdminService;
import cn.geelato.alert.support.ContractCodes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.common.interceptor.annotation.AllowSystemAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警管理端点与事件接入 Controller。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/alert}。
 *
 * <p>端点：admin/trigger / admin/flush / admin/stats / event/exception（@AllowSystemAccess，预留）。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §6
 */
@Slf4j
@ApiRuntimeRestController("/alert")
public class AlertAdminController {

    @Autowired
    private AlertAdminService adminService;

    /** 手动触发 SCHEDULE 规则（同链路执行留痕；仅 SCHEDULE/BOTH 且启用）。 */
    @PostMapping("/admin/trigger")
    public ApiResult<Map<String, Object>> trigger(@RequestBody TriggerRequest req) {
        if (req == null || req.alertCode == null || req.alertCode.isBlank()) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, "alertCode 必填");
        }
        if (req.appId == null || req.appId.isBlank()) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, "appId 必填");
        }
        Map<String, Object> result = adminService.trigger(req.alertCode.trim(), req.appId.trim());
        if ("NOT_FOUND".equals(result.get("reason"))) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + req.alertCode);
        }
        if ("RULE_DISABLED".equals(result.get("reason"))) {
            return ContractCodes.fail(ContractCodes.RULE_DISABLED, "规则已停用，动作拒绝");
        }
        return ApiResult.success(result);
    }

    /** 手动冲刷聚合缓冲（运维排障：指定 alertCode 或 all=true）。 */
    @PostMapping("/admin/flush")
    public ApiResult<Map<String, Object>> flush(@RequestBody(required = false) FlushRequest req) {
        boolean all = req != null && Boolean.TRUE.equals(req.all);
        String alertCode = req == null ? null : req.alertCode;
        if (!all && (alertCode == null || alertCode.isBlank())) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, "须指定 alertCode 或 all=true");
        }
        return ApiResult.success(adminService.flush(alertCode, all));
    }

    /** 发送统计（按 日/规则/触发类型 聚合；message 侧 sent/fail 明细回查留后续）。 */
    @GetMapping("/admin/stats")
    public ApiResult<List<Map<String, Object>>> stats(
            @RequestParam(required = false) String alertCode,
            @RequestParam(required = false) Date from,
            @RequestParam(required = false) Date to) {
        return ApiResult.success(adminService.stats(alertCode, from, to));
    }

    /**
     * 异常事件外部接入（预留：告警引擎拆独立部署时替代同宿主应用事件；内嵌形态不走此接口）。
     *
     * <p>SystemToken 鉴权由平台拦截器处理（@AllowSystemAccess）；消费引擎留后续，恒 accepted 留痕。
     */
    @AllowSystemAccess
    @PostMapping("/event/exception")
    public ApiResult<Map<String, Object>> exceptionEvent(@RequestBody Map<String, Object> event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", event != null);
        data.put("note", "事件消费引擎（匹配/聚合/渲染/投递）留待后续，本端点仅契约占位");
        return ApiResult.success(data);
    }

    /** 手动触发请求。 */
    public static class TriggerRequest {
        public String alertCode;
        public String appId;
    }

    /** 手动冲刷请求。 */
    public static class FlushRequest {
        public String alertCode;
        public Boolean all;
    }
}
