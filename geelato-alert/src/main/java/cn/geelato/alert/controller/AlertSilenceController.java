package cn.geelato.alert.controller;

import cn.geelato.alert.entity.FmsAlertSilence;
import cn.geelato.alert.service.AlertSilenceService;
import cn.geelato.alert.support.ContractCodes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 静默管理 Controller（告警管理员）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/alert/silence}。
 *
 * <p>端点：pageQuery / save / {id}/revoke（CAS）/ match-preview。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §4
 */
@Slf4j
@ApiRuntimeRestController("/alert/silence")
public class AlertSilenceController {

    @Autowired
    private AlertSilenceService silenceService;

    /** 静默规则分页（生效中/已过期/已解除）。 */
    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) AlertSilenceService.SilenceQueryParams params) {
        AlertSilenceService.SilenceQueryParams p = params == null
                ? new AlertSilenceService.SilenceQueryParams() : params;
        return ApiResult.success(silenceService.pageQuery(p));
    }

    /** 新建/编辑（reason 必填；ONCE/RECURRING 窗口校验）。 */
    @PostMapping("/save")
    public ApiResult<Map<String, Object>> save(@RequestBody AlertSilenceService.SilenceSaveRequest req) {
        try {
            FmsAlertSilence saved = silenceService.save(req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("silenceName", saved.getSilenceName());
            data.put("status", saved.getStatus());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        } catch (IllegalStateException e) {
            return ContractCodes.fail(ContractCodes.STATUS_CONFLICT, e.getMessage());
        }
    }

    /** 手动解除（CAS：ACTIVE → REVOKED；立即生效）。 */
    @PostMapping("/{id}/revoke")
    public ApiResult<Map<String, Object>> revoke(@PathVariable String id) {
        int rows = silenceService.revoke(id);
        if (rows == 0) {
            return ContractCodes.fail(ContractCodes.STATUS_CONFLICT, "静默状态已被变更（已解除或已过期）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "REVOKED");
        return ApiResult.success(data);
    }

    /** 静默试匹配：给定假想告警（规则+异常属性）→ 命中哪些生效中的静默（配置前预判影响面）。 */
    @PostMapping("/match-preview")
    public ApiResult<Map<String, Object>> matchPreview(@RequestBody Map<String, Object> hypothetical) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchedSilences", silenceService.matchPreview(hypothetical));
        return ApiResult.success(data);
    }
}
