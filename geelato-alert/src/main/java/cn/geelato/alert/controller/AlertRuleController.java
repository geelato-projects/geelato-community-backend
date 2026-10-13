package cn.geelato.alert.controller;

import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.service.AlertAdminService;
import cn.geelato.alert.service.AlertRecordService;
import cn.geelato.alert.service.AlertRuleService;
import cn.geelato.alert.support.ContractCodes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则管理 Controller（告警管理员）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/alert/rule}。
 *
 * <p>端点：pageQuery（withStats 附带近 7 日发送量）/ {id} / save / {id}/enable / {id}/disable /
 * import-yaml / {id}/export-yaml / {id}/trial-run / {id}/records。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §2
 */
@Slf4j
@ApiRuntimeRestController("/alert/rule")
public class AlertRuleController {

    @Autowired
    private AlertRuleService ruleService;

    @Autowired
    private AlertAdminService adminService;

    @Autowired
    private AlertRecordService recordService;

    /** 规则分页（含编辑器原文 document，供前端无损组装全快照；withStats=1 附带近 7 日发送量）。 */
    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) AlertRuleService.RuleQueryParams params,
            @RequestParam(required = false, defaultValue = "0") int withStats) {
        AlertRuleService.RuleQueryParams p = params == null
                ? new AlertRuleService.RuleQueryParams() : params;
        Map<String, Object> result = ruleService.pageQuery(p);
        if (withStats == 1) {
            List<String> codes = new ArrayList<>();
            for (Object item : (List<?>) result.get("list")) {
                Object code = ((Map<?, ?>) item).get("alertCode");
                if (code != null) {
                    codes.add(String.valueOf(code));
                }
            }
            Map<String, Object> stats = adminService.sendStats7d(codes);
            for (Object item : (List<?>) result.get("list")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;
                row.put("sendCount7d", stats.get(row.get("alertCode")));
            }
        }
        return ApiResult.success(result);
    }

    /** 规则详情（全字段 + document + 引用异常规则编码角标）。 */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id, @RequestParam String appId) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        return ApiResult.success(ruleService.detail(rule));
    }

    /** 新增/编辑（规范化列 + definition_json 双写，version 递增）。 */
    @PostMapping("/save")
    public ApiResult<Map<String, Object>> save(@RequestBody AlertRuleService.RuleSaveRequest req) {
        try {
            FmsAlertRule saved = ruleService.save(req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("alertCode", saved.getAlertCode());
            data.put("version", saved.getVersion());
            data.put("status", saved.getEnableStatus() == 1 ? "enabled" : "disabled");
            data.put("document", req.getDocument());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        }
    }

    /** 启用（经契约门禁：结构 + 必填矩阵校验）。 */
    @PostMapping("/{id}/enable")
    public ApiResult<Map<String, Object>> enable(@PathVariable String id, @RequestParam String appId) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        Map<String, Object> result = ruleService.enable(rule);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return ContractCodes.fail(ContractCodes.CONTRACT_CHECK_FAILED, "契约门禁未通过");
        }
        return ApiResult.success(result);
    }

    /** 停用（热更新；停用不影响未冲刷缓冲补发）。 */
    @PostMapping("/{id}/disable")
    public ApiResult<Map<String, Object>> disable(@PathVariable String id, @RequestParam String appId) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        ruleService.disable(rule);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "disabled");
        return ApiResult.success(data);
    }

    /** YAML 导入（重复导入同 code = 更新，幂等）。请求体为 text/yaml 原文（含内联模板）。 */
    @PostMapping("/import-yaml")
    public ApiResult<Map<String, Object>> importYaml(@RequestBody String yaml, @RequestParam String appId) {
        try {
            FmsAlertRule saved = ruleService.importYaml(yaml, appId);
            List<Map<String, Object>> issues = ruleService.contractCheck(saved);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("alertCode", saved.getAlertCode());
            data.put("version", saved.getVersion());
            data.put("issues", issues);
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ContractCodes.fail(ContractCodes.PARAM_INVALID, e.getMessage());
        }
    }

    /** 导出 YAML（含内联模板，归档/迁移）。 */
    @GetMapping("/{id}/export-yaml")
    public ApiResult<Map<String, Object>> exportYaml(@PathVariable String id, @RequestParam String appId) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertCode", rule.getAlertCode());
        data.put("yaml", ruleService.exportYaml(rule));
        return ApiResult.success(data);
    }

    /** 试跑（样本事件干跑：匹配结果 + 命中量预估 + 渲染预览；只读不落库不投递）。 */
    @PostMapping("/{id}/trial-run")
    public ApiResult<Map<String, Object>> trialRun(@PathVariable String id, @RequestParam String appId,
                                                   @RequestBody(required = false) AlertRuleService.TrialRunRequest req) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        if (rule.getEnableStatus() != 1) {
            return ContractCodes.fail(ContractCodes.RULE_DISABLED, "规则已停用，动作拒绝");
        }
        return ApiResult.success(ruleService.trialRun(rule,
                req == null ? null : req.getSampleEvent(),
                req == null ? null : req.getReplayHours()));
    }

    /** 该规则的告警记录入口（等同 record/pageQuery 加 rule 过滤）。 */
    @GetMapping("/{id}/records")
    public ApiResult<Map<String, Object>> records(@PathVariable String id, @RequestParam String appId,
                                                  @RequestParam(required = false, defaultValue = "1") int pageNum,
                                                  @RequestParam(required = false, defaultValue = "20") int pageSize) {
        FmsAlertRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ContractCodes.fail(ContractCodes.NOT_FOUND, "规则不存在: " + id);
        }
        AlertRecordService.RecordQueryParams params = new AlertRecordService.RecordQueryParams();
        params.appId = appId;
        params.alertCode = rule.getAlertCode();
        params.pageNum = Math.max(1, pageNum);
        params.pageSize = pageSize;
        return ApiResult.success(recordService.pageQuery(params));
    }
}
