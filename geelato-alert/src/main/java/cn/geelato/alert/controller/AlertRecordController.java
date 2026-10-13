package cn.geelato.alert.controller;

import cn.geelato.alert.entity.FmsAlertRecord;
import cn.geelato.alert.entity.FmsAlertSilence;
import cn.geelato.alert.service.AlertRecordService;
import cn.geelato.alert.service.AlertSilenceService;
import cn.geelato.alert.support.ContractCodes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警记录 Controller（告警管理员 / 接收人自查）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/alert/record}。
 *
 * <p>端点：pageQuery / {id} / {id}/messages / {id}/resend / {id}/silence-from。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §5
 */
@Slf4j
@ApiRuntimeRestController("/alert/record")
public class AlertRecordController {

    @Autowired
    private AlertRecordService recordService;

    @Autowired
    private AlertSilenceService silenceService;

    /** 记录分页（告警规则/触发类型/状态/渠道/接收人/时间窗；接收人视角=发给我的）。 */
    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) AlertRecordService.RecordQueryParams params) {
        AlertRecordService.RecordQueryParams p = params == null
                ? new AlertRecordService.RecordQueryParams() : params;
        return ApiResult.success(recordService.pageQuery(p));
    }

    /** 记录详情：渲染快照 + 命中异常清单 + 接收人 + 投递明细索引。 */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) {
        FmsAlertRecord record = recordService.getById(id);
        if (record == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "告警记录不存在: " + id);
        }
        return ApiResult.success(recordService.detail(record));
    }

    /** 投递明细：biz_key 关联 platform_msg 状态（渠道/接收人/重试/死信原因）。 */
    @GetMapping("/{id}/messages")
    public ApiResult<Map<String, Object>> messages(@PathVariable String id) {
        FmsAlertRecord record = recordService.getById(id);
        if (record == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "告警记录不存在: " + id);
        }
        return ApiResult.success(recordService.messages(record));
    }

    /** 手动重发（FAIL/PARTIAL：渲染快照重投，不重渲染；CAS 抢占防并发）。 */
    @PostMapping("/{id}/resend")
    public ApiResult<Map<String, Object>> resend(@PathVariable String id) {
        FmsAlertRecord record = recordService.getById(id);
        if (record == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "告警记录不存在: " + id);
        }
        Map<String, Object> outcome = recordService.resend(record);
        Object status = outcome.get("status");
        if (!Boolean.TRUE.equals(outcome.get("success"))
                && !"FAIL".equals(status) && !"PARTIAL".equals(status)) {
            // 可重发状态（FAIL/PARTIAL）的失败为 CAS 抢占冲突；其余状态失败为前置校验拒绝
            return ContractCodes.fail(ContractCodes.STATUS_CONFLICT,
                    String.valueOf(outcome.get("conflictMessage")));
        }
        return ApiResult.success(outcome);
    }

    /** 从本记录一键创建静默（预填命中维度：该客户该规则，默认 24h）。 */
    @PostMapping("/{id}/silence-from")
    public ApiResult<Map<String, Object>> silenceFrom(@PathVariable String id,
                                                      @RequestBody(required = false) AlertRecordService.SilenceFromRequest req) {
        FmsAlertRecord record = recordService.getById(id);
        if (record == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "告警记录不存在: " + id);
        }
        try {
            FmsAlertSilence silence = silenceService.createFromRecord(record,
                    recordService.firstCustomerId(record),
                    req == null ? null : req.getHours(),
                    req == null ? null : req.getReason());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("silenceId", silence.getId());
            data.put("silenceName", silence.getSilenceName());
            data.put("status", silence.getStatus());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        }
    }
}
