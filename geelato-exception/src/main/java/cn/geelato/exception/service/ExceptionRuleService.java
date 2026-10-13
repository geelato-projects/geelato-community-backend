package cn.geelato.exception.service;

import cn.geelato.exception.engine.core.PipelineEngine;
import cn.geelato.exception.engine.core.PipelineOutcome;
import cn.geelato.exception.engine.core.TriggerMode;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.support.SessionCtxHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常规则服务：目录分页 / 详情 / 保存（无损 round-trip）/ 启停（契约门禁）/ YAML 导入导出 /
 * 试运行（dry-run 占位）/ 升级链查询。
 *
 * <p>契约面 + 持久化深度：规则定义以规范化列为唯一权威持久层（供引擎与查询）；
 * 读取时由列组装编辑器 {@code RuleDocument}（{@link #buildDocument}）回填，
 * 不再落 {@code definition_json} 原文副本。
 *
 * <p>契约门禁（{@link #contractCheck}）仅执行可离线完成的结构 + 必填矩阵 + 脚本预检；
 * trial-run 真实执行引擎（JS/Python 沙箱、{@code $gl.sql} kit）留待后续，返回结构完整的 dry-run 占位。
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §4；数据表设计 §3
 */
@Service
public class ExceptionRuleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 编辑器 RuleDocument.exceptionVersion 字面量（文档格式版本，缺省同值）。 */
    private static final String EXCEPTION_VERSION = "geelato/v1";

    @Autowired
    private ObjectProvider<PipelineEngine> pipelineEngineProvider;

    // ==================== 目录分页 ====================

    /**
     * 规则目录分页（{@code POST /rule/pageQuery}）。
     *
     * @return {@code {list,total,page,pageSize,hasMore}}；行含 summary 字段 + {@code document}（回填原文）
     *         + {@code status}（draft/enabled/disabled）+ {@code version}，供前端组装全快照。
     */
    public Map<String, Object> pageQuery(RuleQueryParams params) {
        int page = Math.max(1, params.pageNum);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));
        // 应用严格隔离：无 appId 不跨应用兜底，直接返回空集（fail-closed）。
        if (!notBlank(params.appId)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("list", new ArrayList<>());
            empty.put("total", 0L);
            empty.put("page", page);
            empty.put("pageSize", pageSize);
            empty.put("hasMore", false);
            return empty;
        }
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()));
        filters.add(Filter.eq("appId", params.appId.trim()));
        filters.add(Filter.eq("delStatus", 0));
        if (params.enableStatus != null) {
            filters.add(Filter.eq("enableStatus", params.enableStatus));
        }
        if (notBlank(params.domainCode)) {
            filters.add(Filter.eq("domainCode", params.domainCode.trim()));
        }
        if (notBlank(params.ruleKind)) {
            filters.add(Filter.eq("ruleKind", params.ruleKind.trim()));
        }
        if (notBlank(params.keyword)) {
            // 扁平 FilterGroup 不支持括号 OR，关键字仅匹配 ruleName
            filters.add(Filter.like("ruleName", "%" + params.keyword.trim() + "%"));
        }
        MetaQuery query = MetaFactory.query(FmsExceptionRule.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.asc("seqNo"), Order.desc("updateAt"));
        PageResult<Map<String, Object>> pageResult = query.page(page, pageSize).page();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : pageResult.getRecords()) {
            items.add(toCatalogRow(rowToEntity(row)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /** 按 id 取规则（当前租户 + 指定应用）；不存在或跨应用返回 null。 */
    public FmsExceptionRule getById(String id, String appId) {
        if (!notBlank(appId)) {
            return null;
        }
        MetaQuery query = MetaFactory.query(FmsExceptionRule.class)
                .where(Filter.eq("id", id),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 按 ruleCode 取规则（当前租户 + 指定应用）；不存在或跨应用返回 null。 */
    public FmsExceptionRule getByCode(String ruleCode, String appId) {
        if (!notBlank(appId)) {
            return null;
        }
        MetaQuery query = MetaFactory.query(FmsExceptionRule.class)
                .where(Filter.eq("ruleCode", ruleCode),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 规则详情：全字段 + 编辑器原文 document + 升级链上下游。 */
    public Map<String, Object> detail(FmsExceptionRule rule) {
        Map<String, Object> map = toCatalogRow(rule);
        map.put("escalationOf", rule.getEscalationOfRuleCode());
        map.put("updatedAt", rule.getUpdateAt() == null ? null : rule.getUpdateAt().toInstant().toString());
        return map;
    }

    // ==================== 保存 ====================

    /**
     * 新增/编辑规则（{@code POST /rule/save}）。
     *
     * <p>文档解析入规范化列；与库内列重建的文档比对，实质变更时 {@code version} 递增。
     *
     * @return 保存后的规则（含 id/version/document）
     */
    public FmsExceptionRule save(RuleSaveRequest req) {
        JsonNode doc = req.getDocument();
        if (doc == null || doc.isNull()) {
            throw new IllegalArgumentException("规则文档不能为空");
        }
        String ruleCode = textAt(doc, "metadata", "code");
        if (!notBlank(ruleCode)) {
            throw new IllegalArgumentException("metadata.code 不能为空");
        }
        if (!notBlank(req.getAppId())) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        // 保存时契约门禁：结构/必填/语法不通过直接拒绝，不落库（检查结果不持久化）
        List<Map<String, Object>> gateIssues = documentGate(doc);
        if (!gateIssues.isEmpty()) {
            throw new IllegalArgumentException("规则文档契约门禁未通过: " + writeJson(gateIssues));
        }
        FmsExceptionRule candidate = new FmsExceptionRule();
        applyDocument(candidate, doc);
        List<Map<String, Object>> contractIssues = contractCheck(candidate);
        if (!contractIssues.isEmpty()) {
            throw new IllegalArgumentException("规则文档契约门禁未通过: " + writeJson(contractIssues));
        }
        FmsExceptionRule existing = notBlank(req.getId())
                ? getById(req.getId(), req.getAppId())
                : getByCode(ruleCode, req.getAppId());
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        String userName = SessionCtxHelper.getCurrentUserName();
        FmsExceptionRule rule = existing != null ? existing : new FmsExceptionRule();
        // 版本递增：与库内列重建文档比对（两侧同经 applyDocument+buildDocument 规范化），实质变更 +1；新建初始为 1
        if (existing != null) {
            FmsExceptionRule incoming = new FmsExceptionRule();
            applyDocument(incoming, doc);
            boolean changed = definitionChanged(buildDocument(existing), buildDocument(incoming));
            rule.setVersion(changed ? existing.getVersion() + 1 : existing.getVersion());
        } else {
            rule.setVersion(Math.max(1, req.getVersion() == null ? 1 : req.getVersion()));
        }
        applyDocument(rule, doc);
        if (req.getStatus() != null) {
            rule.setEnableStatus("enabled".equals(req.getStatus()) ? 1 : 0);
        }
        rule.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
        rule.setAppId(req.getAppId().trim());
        rule.setUpdateAt(now);
        rule.setUpdater(userId);
        rule.setUpdaterName(userName);
        if (existing == null) {
            rule.setDelStatus(0);
            rule.setCreateAt(now);
            rule.setCreator(userId);
            rule.setCreatorName(userName);
        }
        String savedId = MetaFactory.save(rule);
        if (rule.getId() == null && savedId != null) {
            rule.setId(savedId);
        }
        return rule;
    }

    // ==================== 启停（契约门禁）====================

    /**
     * 启用（{@code POST /rule/{id}/enable}）：保存时已过结构门禁；引擎可用时再跑受限超时
     * dry-run（真实执行只读管道，非数组/契约失败拒绝启用）。门禁结果只随响应返回，不持久化。
     *
     * @return 门禁结果 {success, checkError, status}；success=false 时未启用
     */
    public Map<String, Object> enable(FmsExceptionRule rule) {
        List<Map<String, Object>> issues = contractCheck(rule);
        if (issues.isEmpty()) {
            PipelineOutcome dryRun = dryRunOrNull(rule);
            if (dryRun != null && !"SUCCESS".equals(dryRun.getStatus())) {
                issues.add(issue("spec.pipeline", "CONTRACT_CHECK_FAILED",
                        "dry-run 执行失败（" + dryRun.getStatus() + "）: " + dryRun.getError()));
            }
        }
        if (!issues.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("checkError", issues);
            return result;
        }
        rule.setEnableStatus(1);
        touch(rule);
        MetaFactory.save(rule);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("checkError", List.of());
        result.put("status", "enabled");
        return result;
    }

    /** 停用（{@code POST /rule/{id}/disable}）：置 {@code enable_status=0}。 */
    public void disable(FmsExceptionRule rule) {
        rule.setEnableStatus(0);
        touch(rule);
        MetaFactory.save(rule);
    }

    /**
     * 契约门禁：静态结构 + 必填矩阵 + 出口启发式 +（引擎可用时）语法预检。
     *
     * @return 定位化问题清单（{@code {fieldPath, code, message}}）；空表示通过
     */
    public List<Map<String, Object>> contractCheck(FmsExceptionRule rule) {
        List<Map<String, Object>> issues = new ArrayList<>();
        JsonNode doc = buildDocument(rule);
        if (doc == null) {
            issues.add(issue("document", "BUILD_FAIL", "规则文档组装失败"));
            return issues;
        }
        if (!notBlank(textAt(doc, "metadata", "code"))) {
            issues.add(issue("metadata.code", "REQUIRED", "规则编码必填"));
        }
        if (!notBlank(textAt(doc, "metadata", "name"))) {
            issues.add(issue("metadata.name", "REQUIRED", "规则名称必填"));
        }
        JsonNode steps = doc.path("spec").path("pipeline").path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            issues.add(issue("spec.pipeline.steps", "REQUIRED", "判定管道至少一步"));
        } else {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                if (!notBlank(text(step, "name"))) {
                    issues.add(issue("spec.pipeline.steps[" + i + "].name", "REQUIRED", "步骤名必填"));
                }
                String lang = text(step, "lang");
                if (lang != null && !"js".equals(lang) && !"python".equals(lang)) {
                    issues.add(issue("spec.pipeline.steps[" + i + "].lang", "LANG_INVALID", "步骤语言应为 js/python"));
                }
                if (!notBlank(text(step, "content"))) {
                    issues.add(issue("spec.pipeline.steps[" + i + "].content", "REQUIRED", "步骤脚本不能为空"));
                }
            }
            issues.addAll(cn.geelato.exception.engine.core.HitsContractValidator.checkStepExit(steps));
        }
        if ("EscalateRule".equals(text(doc, "kind")) && !notBlank(textAt(doc, "spec", "escalationOf"))) {
            issues.add(issue("spec.escalationOf", "REQUIRED", "升级规则须关联基础规则"));
        }
        PipelineEngine engine = engineOrNull();
        if (engine != null) {
            try {
                for (var si : engine.parseOnly(rule)) {
                    String[] parts = String.valueOf(si.getCode()).split(":", 2);
                    issues.add(issue(parts[0], parts.length > 1 ? parts[1] : "SYNTAX",
                            "语法错误: " + si.getMessage() + (si.getLine() != null ? "（行 " + si.getLine() + "）" : "")));
                }
            } catch (IllegalArgumentException e) {
                String message = String.valueOf(e.getMessage());
                issues.add(message.contains("未知引擎版本")
                        ? issue("engine_version", "ENGINE_VERSION_INVALID", message)
                        : issue("spec.pipeline", "PARSE_FAIL", message));
            }
        }
        return issues;
    }

    /** 文档级门禁（对原始提交文档，contractCheck 只见重建文档恒合法）：exceptionVersion 缺省 geelato/v1、kind 白名单。 */
    List<Map<String, Object>> documentGate(JsonNode doc) {
        List<Map<String, Object>> issues = new ArrayList<>();
        String exceptionVersion = text(doc, "exceptionVersion");
        if (exceptionVersion != null && !EXCEPTION_VERSION.equals(exceptionVersion)) {
            issues.add(issue("exceptionVersion", "EXCEPTION_VERSION_INVALID",
                    "exceptionVersion 应为 " + EXCEPTION_VERSION + "（缺省同值），当前: " + exceptionVersion));
        }
        String kind = text(doc, "kind");
        if (!"AlertRule".equals(kind) && !"EscalateRule".equals(kind)) {
            issues.add(issue("kind", "KIND_INVALID", "kind 应为 AlertRule/EscalateRule，当前: " + kind));
        }
        return issues;
    }

    private PipelineEngine engineOrNull() {
        return pipelineEngineProvider == null ? null : pipelineEngineProvider.getIfAvailable();
    }

    // ==================== 试运行（dry-run 占位）====================

    /**
     * 试运行（{@code POST /rule/{id}/trial-run}）：引擎可用时真实执行管道（只读、不落库不触达，
     * 受限超时），含运行期 hits 契约兜底；不可用时返回结构完整的 dry-run 占位。
     */
    public Map<String, Object> trialRun(FmsExceptionRule rule, String target) {
        PipelineEngine engine = engineOrNull();
        Map<String, Object> contractCheckResult = new LinkedHashMap<>();
        List<Map<String, Object>> issues = contractCheck(rule);
        contractCheckResult.put("status", issues.isEmpty() ? "PASS" : "FAIL");
        contractCheckResult.put("errors", issues);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("target", target == null ? "SCAN" : target);
        if (engine == null) {
            contractCheckResult.put("warnings", List.of(Map.of("message", "试运行未覆盖正例（引擎未启用，仅静态预检）")));
            List<Map<String, Object>> steps = staticSteps(rule);
            result.put("candidateCount", 0);
            result.put("hitCount", 0);
            result.put("steps", steps);
            result.put("hits", List.of());
            result.put("contractCheck", contractCheckResult);
            result.put("executed", false);
            result.put("note", "试运行引擎（JS/Python 沙箱、$gl.sql kit）未启用，本结果仅静态契约预检");
            return result;
        }
        PipelineOutcome outcome;
        try {
            outcome = engine.execute(rule, TriggerMode.TRIAL, null);
        } catch (Exception e) {
            outcome = new PipelineOutcome();
            outcome.setStatus("ENGINE_ERROR");
            outcome.setError(String.valueOf(e.getMessage()));
            outcome.setValidHits(List.of());
            outcome.setViolations(List.of());
            outcome.setSteps(new ArrayList<>());
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> violation : outcome.getViolations()) {
            warnings.add(Map.of("message", String.valueOf(violation.get("message"))));
        }
        if (outcome.getValidHits() != null && outcome.getValidHits().isEmpty() && warnings.isEmpty()) {
            warnings.add(Map.of("message", "本轮 0 命中，试运行未覆盖正例"));
        }
        if (outcome.getDeprecationWarning() != null) {
            warnings.add(Map.of("message", outcome.getDeprecationWarning()));
        }
        contractCheckResult.put("warnings", warnings);
        result.put("candidateCount", outcome.getCandidateCount());
        result.put("hitCount", outcome.getValidHits() == null ? 0 : outcome.getValidHits().size());
        result.put("steps", outcome.getSteps());
        result.put("hits", outcome.getValidHits());
        result.put("contractCheck", contractCheckResult);
        result.put("engineVersion", outcome.getEngineVersion());
        result.put("executed", !"CONTRACT_FATAL".equals(outcome.getStatus()));
        result.put("pipelineStatus", outcome.getStatus());
        if (outcome.getError() != null) {
            result.put("error", outcome.getError());
        }
        return result;
    }

    private PipelineOutcome dryRunOrNull(FmsExceptionRule rule) {
        PipelineEngine engine = engineOrNull();
        if (engine == null) {
            return null;
        }
        try {
            return engine.execute(rule, TriggerMode.TRIAL, null);
        } catch (Exception e) {
            PipelineOutcome outcome = new PipelineOutcome();
            outcome.setStatus("STEP_FAILED");
            outcome.setError("dry-run 异常: " + e.getMessage());
            outcome.setValidHits(List.of());
            outcome.setViolations(List.of());
            outcome.setSteps(List.of());
            return outcome;
        }
    }

    private List<Map<String, Object>> staticSteps(FmsExceptionRule rule) {
        List<Map<String, Object>> steps = new ArrayList<>();
        JsonNode doc = buildDocument(rule);
        if (doc != null) {
            for (JsonNode step : doc.path("spec").path("pipeline").path("steps")) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", text(step, "name"));
                s.put("lang", text(step, "lang"));
                s.put("status", "SKIPPED");
                s.put("costMs", 0);
                s.put("rows", 0);
                s.put("console", List.of("引擎未启用，未执行"));
                steps.add(s);
            }
        }
        return steps;
    }

    // ==================== YAML 导入导出 ====================

    /** 导出 YAML（{@code GET /rule/{id}/export-yaml}）：由规范化列重建文档转 YAML。 */
    public String exportYaml(FmsExceptionRule rule) {
        JsonNode doc = buildDocument(rule);
        Object root = doc == null ? new LinkedHashMap<>() : MAPPER.convertValue(doc, Object.class);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }

    /**
     * 从 YAML 导入规则（{@code POST /rule/import-yaml}）。
     *
     * @param yaml 编辑器 RuleDocument 的 YAML 原文
     * @return 落库后的规则
     */
    public FmsExceptionRule importYaml(String yaml, String appId) {
        Object loaded;
        try {
            loaded = new Yaml().load(yaml);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 解析失败: " + e.getMessage(), e);
        }
        if (loaded == null) {
            throw new IllegalArgumentException("YAML 内容为空");
        }
        JsonNode doc = MAPPER.valueToTree(loaded);
        RuleSaveRequest req = new RuleSaveRequest();
        req.setDocument(doc);
        req.setAppId(appId);
        req.setStatus("draft");
        return save(req);
    }

    // ==================== 升级链 ====================

    /** 升级链查询（{@code GET /rule/escalation-chain/{ruleCode}}）：沿 escalationOf 向上回溯。 */
    public List<Map<String, Object>> escalationChain(String ruleCode, String appId) {
        List<Map<String, Object>> chain = new ArrayList<>();
        String current = ruleCode;
        int guard = 0;
        while (current != null && guard++ < 32) {
            FmsExceptionRule rule = getByCode(current, appId);
            if (rule == null) {
                break;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("ruleCode", rule.getRuleCode());
            node.put("ruleName", rule.getRuleName());
            node.put("ruleKind", rule.getRuleKind());
            node.put("level", rule.getLevel());
            node.put("enableStatus", rule.getEnableStatus());
            node.put("escalationOf", rule.getEscalationOfRuleCode());
            chain.add(node);
            current = rule.getEscalationOfRuleCode();
        }
        return chain;
    }

    // ==================== 内部辅助 ====================

    private Map<String, Object> toCatalogRow(FmsExceptionRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("ruleCode", rule.getRuleCode());
        map.put("ruleName", rule.getRuleName());
        map.put("domainCode", rule.getDomainCode());
        map.put("level", rule.getLevel());
        map.put("ruleKind", rule.getRuleKind());
        map.put("triggerType", rule.getTriggerType());
        map.put("enableStatus", rule.getEnableStatus());
        map.put("status", rule.getEnableStatus() == 1 ? "enabled" : "disabled");
        map.put("version", rule.getVersion());
        map.put("document", buildDocument(rule));
        return map;
    }

    private void touch(FmsExceptionRule rule) {
        rule.setUpdateAt(new Date());
        rule.setUpdater(SessionCtxHelper.getCurrentUserId());
        rule.setUpdaterName(SessionCtxHelper.getCurrentUserName());
    }

    /** 文档实质变更判定：两侧均为规范化（applyDocument + buildDocument）后的文档树，顺序无关比较。 */
    private boolean definitionChanged(JsonNode oldDoc, JsonNode newDoc) {
        return oldDoc == null || !oldDoc.equals(newDoc);
    }

    /** 文档 → 规范化列（唯一权威持久层）。引擎版本规则侧零入口（恒空=引擎缺省），文档版本不落库。 */
    private void applyDocument(FmsExceptionRule rule, JsonNode doc) {
        rule.setRuleCode(textAt(doc, "metadata", "code"));
        rule.setRuleName(orEmpty(textAt(doc, "metadata", "name")));
        rule.setDomainCode(orEmpty(textAt(doc, "metadata", "domain")));
        rule.setRuleDesc(orEmpty(textAt(doc, "metadata", "desc")));
        rule.setLevel(nullToBlank(textAt(doc, "metadata", "level")));
        rule.setRuleKind(mapKind(textAt(doc, "kind")));
        rule.setEscalationOfRuleCode(nullToBlank(textAt(doc, "spec", "escalationOf")));
        rule.setTriggerType(orDefault(textAt(doc, "spec", "trigger", "type"), "SCAN"));
        rule.setCronExpression(nullToBlank(textAt(doc, "spec", "trigger", "cron")));
        rule.setListenEntities(writeJsonAt(doc.path("spec").path("trigger").path("listen")));
        rule.setPipelineJson(writeJsonAt(doc.path("spec").path("pipeline")));
        rule.setThresholdJson(writeJsonAt(doc.path("spec").path("threshold")));
        rule.setOwnerSelector(writeJsonAt(doc.path("spec").path("participants")));
        rule.setResolvePolicy(orDefault(textAt(doc, "spec", "action", "resolvePolicy"), "ANY"));
        rule.setStormJson(writeJsonAt(doc.path("spec").path("storm")));
        rule.setBizType(nullToBlank(textAt(doc, "spec", "action", "bizType")));
        rule.setBizUrlTemplate(nullToBlank(textAt(doc, "spec", "action", "bizUrlTemplate")));
        rule.setFlowTemplate(nullToBlank(textAt(doc, "spec", "action", "flowTemplate")));
        rule.setMutualExclusiveGroup(nullToBlank(textAt(doc, "spec", "action", "mutualExclusiveGroup")));
        rule.setEngineVersion(null);
    }

    /** 规范化列 → 编辑器 RuleDocument（detail/目录/导出/契约门禁的统一文档来源）。 */
    private JsonNode buildDocument(FmsExceptionRule rule) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("exceptionVersion", EXCEPTION_VERSION);
        doc.put("kind", "ESCALATE".equals(rule.getRuleKind()) ? "EscalateRule" : "AlertRule");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("code", rule.getRuleCode());
        metadata.put("name", rule.getRuleName());
        if (notBlank(rule.getDomainCode())) {
            metadata.put("domain", rule.getDomainCode());
        }
        if (notBlank(rule.getLevel())) {
            metadata.put("level", rule.getLevel());
        }
        if (notBlank(rule.getRuleDesc())) {
            metadata.put("desc", rule.getRuleDesc());
        }
        doc.put("metadata", metadata);
        Map<String, Object> spec = new LinkedHashMap<>();
        if (notBlank(rule.getEscalationOfRuleCode())) {
            spec.put("escalationOf", rule.getEscalationOfRuleCode());
        }
        Map<String, Object> trigger = new LinkedHashMap<>();
        if (notBlank(rule.getTriggerType())) {
            trigger.put("type", rule.getTriggerType());
        }
        if (notBlank(rule.getCronExpression())) {
            trigger.put("cron", rule.getCronExpression());
        }
        JsonNode listen = parseDocumentOrNull(rule.getListenEntities());
        if (listen != null) {
            trigger.put("listen", listen);
        }
        spec.put("trigger", trigger);
        spec.put("pipeline", parseDocumentOrNull(rule.getPipelineJson()));
        JsonNode threshold = parseDocumentOrNull(rule.getThresholdJson());
        if (threshold != null) {
            spec.put("threshold", threshold);
        }
        JsonNode storm = parseDocumentOrNull(rule.getStormJson());
        if (storm != null) {
            spec.put("storm", storm);
        }
        JsonNode participants = parseDocumentOrNull(rule.getOwnerSelector());
        if (participants != null) {
            spec.put("participants", participants);
        }
        Map<String, Object> action = new LinkedHashMap<>();
        if (notBlank(rule.getResolvePolicy())) {
            action.put("resolvePolicy", rule.getResolvePolicy());
        }
        if (notBlank(rule.getBizType())) {
            action.put("bizType", rule.getBizType());
        }
        if (notBlank(rule.getBizUrlTemplate())) {
            action.put("bizUrlTemplate", rule.getBizUrlTemplate());
        }
        if (notBlank(rule.getFlowTemplate())) {
            action.put("flowTemplate", rule.getFlowTemplate());
        }
        if (notBlank(rule.getMutualExclusiveGroup())) {
            action.put("mutualExclusiveGroup", rule.getMutualExclusiveGroup());
        }
        if (!action.isEmpty()) {
            spec.put("action", action);
        }
        doc.put("spec", spec);
        return MAPPER.valueToTree(doc);
    }

    private String mapKind(String kind) {
        return "EscalateRule".equals(kind) ? "ESCALATE" : "ALERT";
    }

    private FmsExceptionRule rowToEntity(Map<String, Object> row) {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setId(str(row.get("id")));
        rule.setRuleCode(str(row.get("ruleCode")));
        rule.setRuleName(str(row.get("ruleName")));
        rule.setDomainCode(str(row.get("domainCode")));
        rule.setRuleDesc(str(row.get("ruleDesc")));
        rule.setLevel(str(row.get("level")));
        rule.setRuleKind(str(row.get("ruleKind")));
        rule.setEscalationOfRuleCode(str(row.get("escalationOfRuleCode")));
        rule.setTriggerType(str(row.get("triggerType")));
        rule.setCronExpression(str(row.get("cronExpression")));
        rule.setListenEntities(str(row.get("listenEntities")));
        rule.setPipelineJson(str(row.get("pipelineJson")));
        rule.setThresholdJson(str(row.get("thresholdJson")));
        rule.setOwnerSelector(str(row.get("ownerSelector")));
        rule.setResolvePolicy(str(row.get("resolvePolicy")));
        rule.setStormJson(str(row.get("stormJson")));
        rule.setEngineVersion(str(row.get("engineVersion")));
        rule.setBizType(str(row.get("bizType")));
        rule.setMutualExclusiveGroup(str(row.get("mutualExclusiveGroup")));
        rule.setFlowTemplate(str(row.get("flowTemplate")));
        rule.setBizUrlTemplate(str(row.get("bizUrlTemplate")));
        rule.setEnableStatus(intVal(row.get("enableStatus")));
        rule.setAppId(str(row.get("appId")));
        rule.setVersion(intVal(row.get("version")));
        rule.setUpdateAt(toDate(row.get("updateAt")));
        rule.setTenantCode(str(row.get("tenantCode")));
        rule.setDelStatus(intVal(row.get("delStatus")));
        return rule;
    }

    private Map<String, Object> issue(String fieldPath, String code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldPath", fieldPath);
        map.put("code", code);
        map.put("message", message);
        return map;
    }

    private String writeJsonAt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return writeJson(node);
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private JsonNode parseDocumentOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private Object parseJsonObjectOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    /** 深度取文本：沿路径逐层 path，末节点 asText。 */
    private String textAt(JsonNode root, String... path) {
        JsonNode node = root;
        for (String p : path) {
            node = node.path(p);
        }
        return text(node);
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /** 取父节点下指定字段的文本值（缺失/null 返回 null）。 */
    private String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode node = parent.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private Integer intAt(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private String nullToBlank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private String orDefault(String s, String def) {
        return notBlank(s) ? s : def;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Integer intObj(Object o) {
        return o == null ? null : intVal(o);
    }

    /** DB datetime 值兼容转换（java.sql.Timestamp / Date / ISO 字符串）。 */
    private Date toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (o instanceof java.time.Instant instant) {
            return java.util.Date.from(instant);
        }
        return null;
    }

    // ==================== DTO ====================

    /** 目录分页查询参数。 */
    public static class RuleQueryParams {
        public String appId;
        public Integer enableStatus;
        public String domainCode;
        public String ruleKind;
        public String keyword;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 规则保存请求（{@code POST /rule/save}）。 */
    @lombok.Data
    public static class RuleSaveRequest {
        /** 空表示按 metadata.code 新增/覆盖。 */
        private String id;
        /** 所属应用ID（platform_app.id），新增必填、编辑须与原归属一致。 */
        private String appId;
        /** 编辑器 RuleDocument 原文。 */
        private com.fasterxml.jackson.databind.JsonNode document;
        /** 期望启用态：draft/enabled/disabled（可空则沿用原态）。 */
        private String status;
        private Integer version;
    }
}
