package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.support.AlertSources;
import cn.geelato.alert.support.SessionCtxHelper;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则服务：目录分页 / 详情 / 保存（无损 round-trip）/ 启停（契约门禁）/ YAML 导入导出 /
 * 试跑（dry-run 占位）/ 规则视角记录入口。
 *
 * <p>契约面 + 持久化深度：规则定义以规范化列为唯一权威持久层；读取时由列组装
 * 编辑器 {@code RuleDocument}（{@link #buildDocument}）回填，不再落 {@code definition_json} 原文副本。
 *
 * <p>试跑的真实渲染/匹配引擎（JS 沙箱、聚合缓冲）留待后续，返回结构完整的 dry-run 占位。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §2；规则YAML结构设计 §2-§3
 */
@Service
public class AlertRuleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 编辑器 RuleDocument.alertVersion 字面量（文档格式版本，缺省同值，风格对齐异常侧 exceptionVersion）。 */
    private static final String ALERT_VERSION = "geelato/v1";

    // ==================== 目录分页 ====================

    /**
     * 规则分页（{@code POST /rule/pageQuery}）：触发模式/聚合模式/启停/关键字筛选。
     *
     * @return {@code {list,total,page,pageSize,hasMore}}；行含 document/status/version，供前端组装全快照
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
        if (notBlank(params.triggerMode)) {
            filters.add(Filter.eq("triggerMode", params.triggerMode.trim()));
        }
        if (notBlank(params.aggregateMode)) {
            filters.add(Filter.eq("aggregateMode", params.aggregateMode.trim()));
        }
        if (notBlank(params.keyword)) {
            // 扁平 FilterGroup 不支持括号 OR，关键字仅匹配 alertName
            filters.add(Filter.like("alertName", "%" + params.keyword.trim() + "%"));
        }
        MetaQuery query = MetaFactory.query(FmsAlertRule.class)
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
    public FmsAlertRule getById(String id, String appId) {
        if (!notBlank(appId)) {
            return null;
        }
        MetaQuery query = MetaFactory.query(FmsAlertRule.class)
                .where(Filter.eq("id", id),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 按 alertCode 取规则（当前租户 + 指定应用）；不存在或跨应用返回 null。 */
    public FmsAlertRule getByCode(String alertCode, String appId) {
        if (!notBlank(appId)) {
            return null;
        }
        MetaQuery query = MetaFactory.query(FmsAlertRule.class)
                .where(Filter.eq("alertCode", alertCode),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 规则详情：全字段 + 编辑器原文 document + 订阅事件源 + 引用的源规则角标（EXCEPTION 源回查 platform_exception_rule 留后续，此处仅列出编码）。 */
    public Map<String, Object> detail(FmsAlertRule rule) {
        Map<String, Object> map = toCatalogRow(rule);
        map.put("match", parseJsonObjectOrNull(rule.getMatchJson()));
        map.put("dataset", parseJsonObjectOrNull(rule.getDatasetJson()));
        map.put("receiver", parseJsonObjectOrNull(rule.getReceiverJson()));
        map.put("route", parseJsonObjectOrNull(rule.getRouteJson()));
        map.put("cronExpression", rule.getCronExpression());
        map.put("groupWaitSeconds", rule.getGroupWaitSeconds());
        map.put("groupIntervalSeconds", rule.getGroupIntervalSeconds());
        map.put("repeatIntervalMinutes", rule.getRepeatIntervalMinutes());
        map.put("maxItems", rule.getMaxItems());
        map.put("dailyLimit", rule.getDailyLimit());
        map.put("updatedAt", rule.getUpdateAt() == null ? null : rule.getUpdateAt().toInstant().toString());
        // 引用校验角标：EXCEPTION 源时列出 match.ruleCodes 引用的源规则编码（启用状态回查由前端/后续联表补齐）
        JsonNode match = parseDocumentOrNull(rule.getMatchJson());
        String source = match == null ? null : text(match, "source");
        map.put("source", source);
        List<String> refCodes = new ArrayList<>();
        if (AlertSources.EXCEPTION.equals(source)) {
            for (JsonNode n : match.path("ruleCodes")) {
                refCodes.add(n.asText());
            }
        }
        map.put("referencedRuleCodes", refCodes);
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
    public FmsAlertRule save(RuleSaveRequest req) {
        JsonNode doc = req.getDocument();
        if (doc == null || doc.isNull()) {
            throw new IllegalArgumentException("规则文档不能为空");
        }
        String alertCode = textAt(doc, "metadata", "code");
        if (!notBlank(alertCode)) {
            throw new IllegalArgumentException("metadata.code 不能为空");
        }
        if (!notBlank(req.getAppId())) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        // 文档级门禁（对原始提交文档，contractCheck 只见重建文档恒合法）：alertVersion 缺省 geelato/v1
        String alertVersion = text(doc, "alertVersion");
        if (alertVersion != null && !ALERT_VERSION.equals(alertVersion)) {
            throw new IllegalArgumentException(
                    "alertVersion 应为 " + ALERT_VERSION + "（缺省同值），当前: " + alertVersion);
        }
        FmsAlertRule existing = notBlank(req.getId())
                ? getById(req.getId(), req.getAppId())
                : getByCode(alertCode, req.getAppId());
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        String userName = SessionCtxHelper.getCurrentUserName();
        FmsAlertRule rule = existing != null ? existing : new FmsAlertRule();
        // 版本递增：与库内列重建文档比对（两侧同经 applyDocument+buildDocument 规范化），实质变更 +1
        if (existing != null) {
            FmsAlertRule incoming = new FmsAlertRule();
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
     * 启用（{@code POST /rule/{id}/enable}）：契约门禁通过才置 {@code enable_status=1}（热更新语义由引擎侧消费）。
     *
     * @return {success, checkStatus, checkError}
     */
    public Map<String, Object> enable(FmsAlertRule rule) {
        List<Map<String, Object>> issues = contractCheck(rule);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!issues.isEmpty()) {
            result.put("success", false);
            result.put("checkStatus", "FAIL");
            result.put("checkError", issues);
            return result;
        }
        rule.setEnableStatus(1);
        touch(rule);
        MetaFactory.save(rule);
        result.put("success", true);
        result.put("checkStatus", "PASS");
        result.put("checkError", List.of());
        result.put("status", "enabled");
        return result;
    }

    /** 停用（{@code POST /rule/{id}/disable}）：置 {@code enable_status=0}，不影响未冲刷缓冲补发。 */
    public void disable(FmsAlertRule rule) {
        rule.setEnableStatus(0);
        touch(rule);
        MetaFactory.save(rule);
    }

    /**
     * 逻辑删除（{@code POST /rule/{id}/delete}）：经 {@link MetaFactory#delete(Class)} 自动转
     * {@code UPDATE del_status=1, delete_at=now}（受限当前租户 + 未删除行），从目录可见集消失。
     *
     * <p>彻底删除（物理行）由运维脚本处理，接口只做软删，符合 {@code UK(alert_code, del_status, delete_at)}。
     */
    public void delete(FmsAlertRule rule) {
        MetaFactory.delete(FmsAlertRule.class)
                .where(Filter.eq("id", rule.getId()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0))
                .execute();
    }

    /**
     * 契约门禁：可离线完成的静态结构 + 必填矩阵校验（对齐接口设计 §1 通用错误码 PARAM_INVALID 定位口径）。
     *
     * @return 定位化问题清单（{@code {fieldPath, code, message}}）；空表示通过
     */
    public List<Map<String, Object>> contractCheck(FmsAlertRule rule) {
        List<Map<String, Object>> issues = new ArrayList<>();
        JsonNode doc = buildDocument(rule);
        if (doc == null) {
            issues.add(issue("document", "BUILD_FAIL", "规则文档组装失败"));
            return issues;
        }
        if (!"AlertRule".equals(text(doc, "kind"))) {
            issues.add(issue("kind", "KIND_INVALID", "kind 应为 AlertRule"));
        }
        if (!notBlank(textAt(doc, "metadata", "code"))) {
            issues.add(issue("metadata.code", "REQUIRED", "告警编码必填"));
        }
        if (!notBlank(textAt(doc, "metadata", "name"))) {
            issues.add(issue("metadata.name", "REQUIRED", "告警名称必填"));
        }
        JsonNode match = doc.path("spec").path("match");
        String source = text(match, "source");
        if (source == null || !AlertSources.KNOWN.contains(source)) {
            issues.add(issue("spec.match.source", "SOURCE_INVALID",
                    "未知事件源: " + source + "（应为 " + AlertSources.KNOWN + "）"));
        }
        String mode = textAt(doc, "spec", "trigger", "mode");
        // EXCEPTION 源事件订阅必填；SCHEDULE 模式走数据集口径不走事件匹配，豁免（接口设计 §6）
        if (AlertSources.EXCEPTION.equals(source) && !"SCHEDULE".equals(mode)) {
            JsonNode events = match.path("events");
            if (!events.isArray() || events.isEmpty()) {
                issues.add(issue("spec.match.events", "REQUIRED", "订阅事件至少一项"));
            }
        }
        if (!List.of("EVENT", "SCHEDULE", "BOTH").contains(mode)) {
            issues.add(issue("spec.trigger.mode", "MODE_INVALID", "trigger.mode 应为 EVENT/SCHEDULE/BOTH"));
        }
        if (("SCHEDULE".equals(mode) || "BOTH".equals(mode)) && !notBlank(textAt(doc, "spec", "trigger", "cron"))) {
            issues.add(issue("spec.trigger.cron", "REQUIRED", "定时/双节奏须配置 cron"));
        }
        String aggMode = textAt(doc, "spec", "aggregate", "mode");
        if (!List.of("INDEPENDENT", "SINGLE", "AGGREGATE", "GROUP").contains(aggMode)) {
            issues.add(issue("spec.aggregate.mode", "MODE_INVALID", "aggregate.mode 应为 INDEPENDENT/SINGLE/AGGREGATE/GROUP"));
        }
        JsonNode channels = doc.path("spec").path("route").path("default").path("channels");
        if (!channels.isArray() || channels.isEmpty()) {
            issues.add(issue("spec.route.default.channels", "REQUIRED", "通道组至少一项"));
        } else {
            for (JsonNode ch : channels) {
                if (!VALID_CHANNELS.contains(ch.asText())) {
                    issues.add(issue("spec.route.default.channels", "CHANNEL_INVALID",
                            "通道码非法: " + ch.asText() + "（应为 " + VALID_CHANNELS + "）"));
                }
            }
        }
        if (!notBlank(textAt(doc, "spec", "route", "default", "template"))) {
            issues.add(issue("spec.route.default.template", "REQUIRED", "模板编码必填"));
        }
        JsonNode selectors = doc.path("spec").path("receivers").path("extra");
        if (!selectors.isArray() || selectors.isEmpty()) {
            issues.add(issue("spec.receivers.extra", "REQUIRED", "接收人选择器至少一项"));
        } else {
            for (JsonNode sel : selectors) {
                String selType = sel.path("type").asText(null);
                if (!VALID_SELECTOR_TYPES.contains(selType)) {
                    issues.add(issue("spec.receivers.extra", "SELECTOR_TYPE_INVALID",
                            "接收人选择器类型非法: " + selType + "（应为 " + VALID_SELECTOR_TYPES + "）"));
                }
            }
        }
        return issues;
    }

    private static final List<String> VALID_CHANNELS = List.of("inapp", "email", "sms", "wework", "bot", "voice");
    private static final List<String> VALID_SELECTOR_TYPES =
            List.of("USER", "ROLE", "DEPT", "DEPT_ROLE", "BIZ_FIELD", "ORG_TREE_UP");

    // ==================== 试跑（dry-run 占位）====================

    /**
     * 试跑（{@code POST /rule/{id}/trial-run}）：结构门禁真实执行；匹配/渲染引擎留后续，
     * 返回结构完整 dry-run 结果（{@code matched=false}、渲染占位、{@code executed=false}）。
     */
    public Map<String, Object> trialRun(FmsAlertRule rule, JsonNode sampleEvent, Integer replayHours) {
        List<Map<String, Object>> issues = contractCheck(rule);
        Map<String, Object> matchDetail = new LinkedHashMap<>();
        matchDetail.put("source", "SKIPPED");
        matchDetail.put("events", "SKIPPED");
        matchDetail.put("ruleCodes", "SKIPPED");
        Map<String, Object> render = new LinkedHashMap<>();
        render.put("variant", "default");
        render.put("title", null);
        render.put("content", null);
        render.put("costMs", 0);
        render.put("console", List.of("渲染沙箱未启用，未执行"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", false);
        result.put("matchDetail", matchDetail);
        result.put("replayCount", 0);
        result.put("replayHours", replayHours == null ? 24 : replayHours);
        result.put("estimatedDaily", 0);
        result.put("render", render);
        result.put("contractCheck", Map.of(
                "status", issues.isEmpty() ? "PASS" : "FAIL",
                "errors", issues));
        result.put("sampleEventAccepted", sampleEvent != null && !sampleEvent.isNull());
        result.put("executed", false);
        result.put("note", "匹配/渲染/投递引擎留待后续，本结果仅静态契约预检（只读不落库不投递）");
        return result;
    }

    // ==================== YAML 导入导出 ====================

    /** 导出 YAML（{@code GET /rule/{id}/export-yaml}）：由规范化列重建文档转 YAML（含内联模板）。 */
    public String exportYaml(FmsAlertRule rule) {
        JsonNode doc = buildDocument(rule);
        Object root = doc == null ? new LinkedHashMap<>() : MAPPER.convertValue(doc, Object.class);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }

    /**
     * 从 YAML 导入规则（{@code POST /rule/import-yaml}）：校验→落库 version+1；
     * 重复导入同 code = 更新（幂等）。内联模板 upsert 由模板引擎侧消费，留后续。
     */
    public FmsAlertRule importYaml(String yaml, String appId) {
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

    // ==================== 内部辅助 ====================

    private Map<String, Object> toCatalogRow(FmsAlertRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("alertCode", rule.getAlertCode());
        map.put("alertName", rule.getAlertName());
        map.put("alertDesc", rule.getAlertDesc());
        map.put("triggerMode", rule.getTriggerMode());
        map.put("aggregateMode", rule.getAggregateMode());
        map.put("enableStatus", rule.getEnableStatus());
        map.put("status", rule.getEnableStatus() == 1 ? "enabled" : "disabled");
        map.put("version", rule.getVersion());
        map.put("document", buildDocument(rule));
        return map;
    }

    private void touch(FmsAlertRule rule) {
        rule.setUpdateAt(new Date());
        rule.setUpdater(SessionCtxHelper.getCurrentUserId());
        rule.setUpdaterName(SessionCtxHelper.getCurrentUserName());
    }

    /** 文档实质变更判定：两侧均为规范化（applyDocument + buildDocument）后的文档树，顺序无关比较。 */
    private boolean definitionChanged(JsonNode oldDoc, JsonNode newDoc) {
        return oldDoc == null || !oldDoc.equals(newDoc);
    }

    /** 文档 → 规范化列（唯一权威持久层）。 */
    void applyDocument(FmsAlertRule rule, JsonNode doc) {
        rule.setAlertCode(textAt(doc, "metadata", "code"));
        rule.setAlertName(orEmpty(textAt(doc, "metadata", "name")));
        rule.setAlertDesc(orEmpty(textAt(doc, "metadata", "desc")));
        rule.setMatchJson(writeJsonAt(normalizeMatch(doc.path("spec").path("match"))));
        rule.setTriggerMode(orDefault(textAt(doc, "spec", "trigger", "mode"), "EVENT"));
        rule.setCronExpression(nullToBlank(textAt(doc, "spec", "trigger", "cron")));
        rule.setDatasetJson(writeJsonAt(doc.path("spec").path("trigger").path("dataset")));
        JsonNode agg = doc.path("spec").path("aggregate");
        rule.setAggregateMode(mapAggregateMode(textAt(doc, "spec", "aggregate", "mode")));
        rule.setGroupWaitSeconds(intAt(agg, "groupWaitSeconds"));
        rule.setGroupIntervalSeconds(intAt(agg, "groupIntervalSeconds"));
        rule.setRepeatIntervalMinutes(intAt(agg, "repeatMinutes"));
        rule.setMaxItems(intAt(agg, "maxItems"));
        Integer resolveNotify = boolToIntAt(agg, "resolveNotify");
        if (resolveNotify != null) {
            rule.setResolveNotify(resolveNotify);
        }
        rule.setReceiverJson(writeJsonAt(doc.path("spec").path("receivers")));
        rule.setRouteJson(writeJsonAt(doc.path("spec").path("route")));
        rule.setDailyLimit(intAt(doc.path("spec").path("noise"), "dailyLimit"));
    }

    /** 规范化列 → 编辑器 RuleDocument（detail/目录/导出/契约门禁的统一文档来源）。 */
    private JsonNode buildDocument(FmsAlertRule rule) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("alertVersion", ALERT_VERSION);
        doc.put("kind", "AlertRule");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("code", rule.getAlertCode());
        metadata.put("name", rule.getAlertName());
        if (notBlank(rule.getAlertDesc())) {
            metadata.put("desc", rule.getAlertDesc());
        }
        doc.put("metadata", metadata);
        Map<String, Object> spec = new LinkedHashMap<>();
        JsonNode match = parseDocumentOrNull(rule.getMatchJson());
        if (match != null) {
            spec.put("match", match);
        }
        Map<String, Object> trigger = new LinkedHashMap<>();
        if (notBlank(rule.getTriggerMode())) {
            trigger.put("mode", rule.getTriggerMode());
        }
        if (notBlank(rule.getCronExpression())) {
            trigger.put("cron", rule.getCronExpression());
        }
        JsonNode dataset = parseDocumentOrNull(rule.getDatasetJson());
        if (dataset != null) {
            trigger.put("dataset", dataset);
        }
        if (!trigger.isEmpty()) {
            spec.put("trigger", trigger);
        }
        Map<String, Object> aggregate = new LinkedHashMap<>();
        if (notBlank(rule.getAggregateMode())) {
            aggregate.put("mode", rule.getAggregateMode());
        }
        if (rule.getGroupWaitSeconds() != null) {
            aggregate.put("groupWaitSeconds", rule.getGroupWaitSeconds());
        }
        if (rule.getGroupIntervalSeconds() != null) {
            aggregate.put("groupIntervalSeconds", rule.getGroupIntervalSeconds());
        }
        if (rule.getRepeatIntervalMinutes() != null) {
            aggregate.put("repeatMinutes", rule.getRepeatIntervalMinutes());
        }
        if (rule.getMaxItems() != null) {
            aggregate.put("maxItems", rule.getMaxItems());
        }
        if (rule.getResolveNotify() == 1) {
            aggregate.put("resolveNotify", true);
        }
        if (!aggregate.isEmpty()) {
            spec.put("aggregate", aggregate);
        }
        JsonNode receivers = parseDocumentOrNull(rule.getReceiverJson());
        if (receivers != null) {
            spec.put("receivers", receivers);
        }
        JsonNode route = parseDocumentOrNull(rule.getRouteJson());
        if (route != null) {
            spec.put("route", route);
        }
        if (rule.getDailyLimit() != null) {
            spec.put("noise", Map.of("dailyLimit", rule.getDailyLimit()));
        }
        doc.put("spec", spec);
        return MAPPER.valueToTree(doc);
    }

    /** match 落库前规范化：缺 source 补默认 EXCEPTION（落库即显式，自描述事件源）。 */
    private JsonNode normalizeMatch(JsonNode match) {
        if (match == null || !match.isObject() || notBlank(text(match, "source"))) {
            return match;
        }
        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("source", AlertSources.EXCEPTION);
        normalized.setAll((ObjectNode) match);
        return normalized;
    }

    /** YAML 口径兼容：SINGLE≈INDEPENDENT（逐条）、GROUP≈AGGREGATE（聚合），落库归一为数据表枚举。 */
    private String mapAggregateMode(String mode) {
        if ("SINGLE".equals(mode)) {
            return "INDEPENDENT";
        }
        if ("GROUP".equals(mode)) {
            return "AGGREGATE";
        }
        return notBlank(mode) ? mode : "INDEPENDENT";
    }

    private FmsAlertRule rowToEntity(Map<String, Object> row) {
        FmsAlertRule rule = new FmsAlertRule();
        rule.setId(str(row.get("id")));
        rule.setAlertCode(str(row.get("alertCode")));
        rule.setAlertName(str(row.get("alertName")));
        rule.setAlertDesc(str(row.get("alertDesc")));
        rule.setMatchJson(str(row.get("matchJson")));
        rule.setTriggerMode(str(row.get("triggerMode")));
        rule.setCronExpression(str(row.get("cronExpression")));
        rule.setDatasetJson(str(row.get("datasetJson")));
        rule.setAggregateMode(str(row.get("aggregateMode")));
        rule.setGroupWaitSeconds(intObj(row.get("groupWaitSeconds")));
        rule.setGroupIntervalSeconds(intObj(row.get("groupIntervalSeconds")));
        rule.setRepeatIntervalMinutes(intObj(row.get("repeatIntervalMinutes")));
        rule.setMaxItems(intObj(row.get("maxItems")));
        rule.setResolveNotify(intVal(row.get("resolveNotify")));
        rule.setReceiverJson(str(row.get("receiverJson")));
        rule.setRouteJson(str(row.get("routeJson")));
        rule.setDailyLimit(intObj(row.get("dailyLimit")));
        rule.setEnableStatus(intVal(row.get("enableStatus")));
        rule.setVersion(intVal(row.get("version")));
        rule.setUpdateAt(toDate(row.get("updateAt")));
        rule.setAppId(str(row.get("appId")));
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
        if (parent == null) {
            return null;
        }
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    /** 布尔字段落 tinyint（缺省 null → 由实体默认值兜底）。 */
    private Integer boolToIntAt(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asBoolean(false) ? 1 : 0;
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

    /** 规则分页查询参数。 */
    public static class RuleQueryParams {
        public String appId;
        public Integer enableStatus;
        public String triggerMode;
        public String aggregateMode;
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
        /** 编辑器 RuleDocument 原文（alertVersion=geelato/v1，缺省同值）。 */
        private com.fasterxml.jackson.databind.JsonNode document;
        /** 期望启用态：draft/enabled/disabled（可空则沿用原态）。 */
        private String status;
        private Integer version;
    }

    /** 试跑请求（{@code POST /rule/{id}/trial-run}）。 */
    @lombok.Data
    public static class TrialRunRequest {
        private com.fasterxml.jackson.databind.JsonNode sampleEvent;
        private Integer replayHours;
    }
}
