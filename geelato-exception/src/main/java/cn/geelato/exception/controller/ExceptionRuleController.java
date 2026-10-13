package cn.geelato.exception.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.service.ExceptionRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 异常规则管理 Controller（管理员/运营）。
 *
 * <p>由 ApiPrefixAutoConfiguration 自动加 /api 前缀，实际路径 = {@code /api/exception/rule}。
 *
 * <p>端点：pageQuery / {id} / save / {id}/enable / {id}/disable / import-yaml /
 * {id}/export-yaml / {id}/trial-run / escalation-chain/{ruleCode}。
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §4
 */
@ApiRuntimeRestController("/exception/rule")
public class ExceptionRuleController {

    @Autowired
    private ExceptionRuleService ruleService;

    /** 规则目录分页（含编辑器原文 document，供前端无损组装全快照）。 */
    @PostMapping("/pageQuery")
    public ApiResult<Map<String, Object>> pageQuery(
            @RequestBody(required = false) ExceptionRuleService.RuleQueryParams params) {
        ExceptionRuleService.RuleQueryParams p = params == null
                ? new ExceptionRuleService.RuleQueryParams() : params;
        return ApiResult.success(ruleService.pageQuery(p));
    }

    /** 规则详情（全字段 + document + 升级链上游）。 */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id,
                                                 @RequestParam String appId) {
        FmsExceptionRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ApiResult.fail(40400, "规则不存在: " + id);
        }
        return ApiResult.success(ruleService.detail(rule));
    }

    /** 新增/编辑（规范化列 + definition_json 双写，version 递增）。 */
    @PostMapping("/save")
    public ApiResult<Map<String, Object>> save(@RequestBody ExceptionRuleService.RuleSaveRequest req) {
        try {
            FmsExceptionRule saved = ruleService.save(req);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("ruleCode", saved.getRuleCode());
            data.put("version", saved.getVersion());
            data.put("status", saved.getEnableStatus() == 1 ? "enabled" : "disabled");
            data.put("document", req.getDocument());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
    }

    /** 启用（经契约门禁：结构 + 必填矩阵 + 脚本预检）。 */
    @PostMapping("/{id}/enable")
    public ApiResult<Map<String, Object>> enable(@PathVariable String id,
                                                 @RequestParam String appId) {
        FmsExceptionRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ApiResult.fail(40400, "规则不存在: " + id);
        }
        Map<String, Object> result = ruleService.enable(rule);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return ApiResult.fail(42200, "契约门禁未通过");
        }
        return ApiResult.success(result);
    }

    /** 停用（热更新）。 */
    @PostMapping("/{id}/disable")
    public ApiResult<Map<String, Object>> disable(@PathVariable String id,
                                                  @RequestParam String appId) {
        FmsExceptionRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ApiResult.fail(40400, "规则不存在: " + id);
        }
        ruleService.disable(rule);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("status", "disabled");
        return ApiResult.success(data);
    }

    /** YAML 导入（整文件解析 → 校验 → 落库）。请求体为 text/yaml 原文。 */
    @PostMapping("/import-yaml")
    public ApiResult<Map<String, Object>> importYaml(@RequestBody String yaml,
                                                     @RequestParam String appId) {
        try {
            FmsExceptionRule saved = ruleService.importYaml(yaml, appId);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("id", saved.getId());
            data.put("ruleCode", saved.getRuleCode());
            data.put("version", saved.getVersion());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
    }

    /** 导出 YAML（归档/迁移）。 */
    @GetMapping(value = "/{id}/export-yaml", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, Object>> exportYaml(@PathVariable String id,
                                                     @RequestParam String appId) {
        FmsExceptionRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ApiResult.fail(40400, "规则不存在: " + id);
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("ruleCode", rule.getRuleCode());
        data.put("yaml", ruleService.exportYaml(rule));
        return ApiResult.success(data);
    }

    /** 试运行（契约门禁真实，管道执行 dry-run 占位）。 */
    @PostMapping("/{id}/trial-run")
    public ApiResult<Map<String, Object>> trialRun(@PathVariable String id,
                                                   @RequestParam String appId,
                                                   @RequestBody(required = false) TrialRunRequest req) {
        FmsExceptionRule rule = ruleService.getById(id, appId);
        if (rule == null) {
            return ApiResult.fail(40400, "规则不存在: " + id);
        }
        String target = req == null || req.getTarget() == null ? "SCAN" : req.getTarget();
        return ApiResult.success(ruleService.trialRun(rule, target));
    }

    /** 升级链查询（链上规则 + 漂移标记）。 */
    @GetMapping("/escalation-chain/{ruleCode}")
    public ApiResult<List<Map<String, Object>>> escalationChain(@PathVariable String ruleCode,
                                                               @RequestParam String appId) {
        return ApiResult.success(ruleService.escalationChain(ruleCode, appId));
    }

    /** 契约门禁独立预检（保存前校验，返回 issue 清单）。 */
    @PostMapping("/contract-check")
    public ApiResult<Map<String, Object>> contractCheck(
            @RequestBody ExceptionRuleService.RuleSaveRequest req) {
        try {
            FmsExceptionRule preview = ruleService.save(req);
            List<Map<String, Object>> issues = ruleService.contractCheck(preview);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("status", issues.isEmpty() ? "PASS" : "FAIL");
            data.put("errors", issues);
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
    }

    /** 试运行请求。 */
    @lombok.Data
    public static class TrialRunRequest {
        private String target;
        private Map<String, Object> params;
    }
}
