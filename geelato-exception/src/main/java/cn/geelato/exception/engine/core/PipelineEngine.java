package cn.geelato.exception.engine.core;

import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptIssue;
import cn.geelato.script.core.ScriptResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 判定管道引擎（异常领域编排）：解析规则 steps 并逐步执行 geelato-script 单脚本引擎，
 * 上一步输出注入下一步 $input；三态复用同一条管道——SCAN 全量 / RECHECK 注入 $ctx.bizId 单对象 /
 * TRIAL 受限超时 dry-run。candidateCount = 各步 SQL 行数计数（$gl.dao kit 口径）累计。
 * 步骤出口统一做 snake→camelCase 归一化与大小熔断；最终返回经 hits 契约兜底，
 * bizType 脚本返回优先、规则值兜底（回溯坐标 bizType+bizId）。
 */
@Slf4j
public class PipelineEngine {

    public static final String PROFILE_ID = "exception-judge";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScriptEngine scriptEngine;
    private final long stepTimeoutMs;
    private final long pipelineTimeoutMs;
    private final long trialTimeoutMs;
    private final long stepOutputMaxBytes;

    public PipelineEngine(ScriptEngine scriptEngine, long stepTimeoutMs, long pipelineTimeoutMs, long trialTimeoutMs,
                          long stepOutputMaxBytes) {
        this.scriptEngine = scriptEngine;
        this.stepTimeoutMs = stepTimeoutMs;
        this.pipelineTimeoutMs = pipelineTimeoutMs;
        this.trialTimeoutMs = trialTimeoutMs;
        this.stepOutputMaxBytes = stepOutputMaxBytes;
    }

    public PipelineOutcome execute(FmsExceptionRule rule, TriggerMode trigger, String bizId) {
        long start = System.currentTimeMillis();
        boolean trial = trigger == TriggerMode.TRIAL;
        long pipelineBudget = trial ? trialTimeoutMs : pipelineTimeoutMs;

        List<JsonNode> steps = parseSteps(rule.getPipelineJson());
        PipelineOutcome outcome = new PipelineOutcome();
        outcome.setValidHits(List.of());
        outcome.setViolations(List.of());
        outcome.setSteps(new ArrayList<>());
        if (steps.isEmpty()) {
            outcome.setStatus("CONTRACT_FATAL");
            outcome.setError("判定管道为空（pipeline_json.steps）");
            log.warn("管道无步骤 rule={}", rule.getRuleCode());
            return outcome;
        }
        log.info("管道开始 rule={} trigger={} bizId={} steps={}", rule.getRuleCode(), trigger.name(), bizId, steps.size());

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("trigger", trigger.name());
        ctx.put("bizId", bizId);
        ctx.put("rule", Map.of(
                "code", nz(rule.getRuleCode()),
                "level", nz(rule.getLevel()),
                "kind", nz(rule.getRuleKind())));
        Map<String, Object> baseBindings = new LinkedHashMap<>();
        baseBindings.put("$ctx", ctx);
        baseBindings.put("$params", parseParams(rule.getThresholdJson()));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ruleCode", nz(rule.getRuleCode()));
        attributes.put("escalationOf", nz(rule.getEscalationOfRuleCode()));
        attributes.put("bizType", nz(rule.getBizType()));
        attributes.put("tenantCode", nz(rule.getTenantCode()));
        attributes.put("appId", nz(rule.getAppId()));
        String engineVersion = resolveEngineVersion(rule);

        Object input = null;
        long candidateCount = 0;
        ScriptResult lastResult = null;
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            String name = text(step, "name") == null ? "step-" + (i + 1) : text(step, "name");
            String lang = text(step, "lang") == null ? "js" : text(step, "lang");
            long stepStart = System.currentTimeMillis();
            long budget = trial ? trialTimeoutMs : Math.min(stepTimeoutMs, Math.max(1, pipelineBudget - (stepStart - start)));
            if (!trial && stepStart - start >= pipelineBudget) {
                outcome.setStatus("PIPELINE_TIMEOUT");
                outcome.setCandidateCount(candidateCount);
                outcome.setCostMs(System.currentTimeMillis() - start);
                outcome.setError("管道总预算耗尽（" + pipelineBudget + "ms），止于步骤 " + name);
                outcome.getSteps().add(reportOf(name, lang, "SKIPPED", 0, 0, List.of(), null));
                return outcome;
            }

            Map<String, Object> bindings = new LinkedHashMap<>(baseBindings);
            if (i > 0 && input != null) {
                bindings.put("$input", input);
            }
            ScriptResult result = scriptEngine.execute(ScriptExecution.builder()
                    .lang(lang)
                    .content(text(step, "content") == null ? "" : text(step, "content"))
                    .scriptName(nz(rule.getRuleCode()) + "/" + name)
                    .engineVersion(engineVersion)
                    .profileId(PROFILE_ID)
                    .bindings(bindings)
                    .attributes(attributes)
                    .timeoutMs(budget)
                    .build());
            lastResult = result;
            candidateCount += result.getRowCount();
            String scriptError = result.getError() == null ? null
                    : result.getError().getType() + ": " + result.getError().getMessage();
            if (!"SUCCESS".equals(result.getStatus())) {
                outcome.getSteps().add(reportOf(name, lang, result.getStatus(), System.currentTimeMillis() - stepStart,
                        result.getRowCount(), result.getConsole(), scriptError));
                outcome.setStatus("STEP_FAILED");
                outcome.setCandidateCount(candidateCount);
                outcome.setCostMs(System.currentTimeMillis() - start);
                outcome.setEngineVersion(result.getEngineVersion());
                outcome.setDeprecationWarning(result.getDeprecationWarning());
                outcome.setError("步骤 " + name + " " + (scriptError == null ? result.getStatus() : scriptError));
                log.warn("步骤失败 rule={}/{} status={} error={}", rule.getRuleCode(), name, result.getStatus(),
                        scriptError == null ? result.getStatus() : scriptError);
                return outcome;
            }
            Object output = KeyCaseNormalizer.normalize(result.getValue());
            String outputIssue = checkStepOutput(output);
            outcome.getSteps().add(reportOf(name, lang, outputIssue == null ? "SUCCESS" : "STEP_FAILED",
                    System.currentTimeMillis() - stepStart, result.getRowCount(), result.getConsole(),
                    outputIssue == null ? null : outputIssue));
            if (outputIssue != null) {
                outcome.setStatus("STEP_FAILED");
                outcome.setCandidateCount(candidateCount);
                outcome.setCostMs(System.currentTimeMillis() - start);
                outcome.setEngineVersion(result.getEngineVersion());
                outcome.setDeprecationWarning(result.getDeprecationWarning());
                outcome.setError("步骤 " + name + " " + outputIssue);
                log.warn("步骤失败 rule={}/{} error={}", rule.getRuleCode(), name, outputIssue);
                return outcome;
            }
            input = output;
            log.info("步骤完成 rule={}/{} lang={} costMs={} rows={}", rule.getRuleCode(), name, lang,
                    System.currentTimeMillis() - stepStart, result.getRowCount());
        }

        outcome.setCandidateCount(candidateCount);
        outcome.setCostMs(System.currentTimeMillis() - start);
        if (lastResult != null) {
            outcome.setEngineVersion(lastResult.getEngineVersion());
            outcome.setDeprecationWarning(lastResult.getDeprecationWarning());
        }
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(input);
        if (check.fatal()) {
            outcome.setStatus("CONTRACT_FATAL");
            outcome.setViolations(check.violations());
            outcome.setError(check.violations().isEmpty() ? "hits 契约失败" : String.valueOf(check.violations().get(0).get("message")));
            log.warn("hits 契约致命失败 rule={} violations={}", rule.getRuleCode(), check.violations());
            return outcome;
        }
        for (int i = 0; i < check.validHits().size(); i++) {
            Hit hit = check.validHits().get(i);
            hit.defaultBizType(rule.getBizType());
            if (hit.getBizType() == null || hit.getBizType().isBlank()) {
                check.violations().add(Map.of(
                        "fieldPath", "hits[" + i + "].bizType", "code", "BIZ_TYPE_EMPTY",
                        "message", "第 " + i + " 条命中 bizType 最终为空（脚本与规则均未提供），回溯坐标缺失"));
            }
        }
        outcome.setStatus("SUCCESS");
        outcome.setValidHits(check.validHits());
        outcome.setViolations(check.violations());
        log.info("管道完成 rule={} status=SUCCESS validHits={} violations={} candidateCount={} costMs={}",
                rule.getRuleCode(), check.validHits().size(), check.violations().size(), candidateCount,
                System.currentTimeMillis() - start);
        return outcome;
    }

    /** 步骤出口轻量校验：JSON 可序列化 + 大小熔断（返回 null=通过，否则为失败原因）。 */
    private String checkStepOutput(Object output) {
        String json;
        try {
            json = MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return "STEP_OUTPUT_NOT_SERIALIZABLE（输出无法 JSON 序列化: " + e.getMessage() + "）";
        }
        if (json != null && json.length() > stepOutputMaxBytes) {
            return "STEP_OUTPUT_OVERSIZE（输出 " + json.length() + " 字节超过上限 " + stepOutputMaxBytes + " 字节）";
        }
        return null;
    }

    /** 语法预检（不执行），供保存契约门禁。 */
    public List<ScriptIssue> parseOnly(FmsExceptionRule rule) {
        List<ScriptIssue> issues = new ArrayList<>();
        int index = 0;
        for (JsonNode step : parseSteps(rule.getPipelineJson())) {
            String lang = text(step, "lang") == null ? "js" : text(step, "lang");
            String content = text(step, "content") == null ? "" : text(step, "content");
            for (ScriptIssue issue : scriptEngine.parseOnly(lang, content, resolveEngineVersion(rule))) {
                issue.setCode("steps[" + index + "].content:" + issue.getCode());
                issues.add(issue);
            }
            index++;
        }
        return issues;
    }

    private static Map<String, Object> reportOf(String name, String lang, String status, long costMs, long rows,
                                                List<String> console, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("lang", lang);
        m.put("status", status);
        m.put("costMs", costMs);
        m.put("rows", rows);
        m.put("console", console == null ? List.of() : console);
        if (error != null) {
            m.put("error", error);
        }
        return m;
    }

    private static String resolveEngineVersion(FmsExceptionRule rule) {
        return rule.getEngineVersion() == null || rule.getEngineVersion().isBlank()
                ? null : rule.getEngineVersion();
    }

    private static List<JsonNode> parseSteps(String pipelineJson) {
        List<JsonNode> steps = new ArrayList<>();
        JsonNode root = parseOrNull(pipelineJson);
        if (root == null) {
            return steps;
        }
        for (JsonNode node : root.path("steps")) {
            steps.add(node);
        }
        return steps;
    }

    private static Map<String, Object> parseParams(String thresholdJson) {
        JsonNode root = parseOrNull(thresholdJson);
        if (root == null || root.isNull()) {
            return Map.of();
        }
        return MAPPER.convertValue(root, Map.class);
    }

    private static JsonNode parseOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
