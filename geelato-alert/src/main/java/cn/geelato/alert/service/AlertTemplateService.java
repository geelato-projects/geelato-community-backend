package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.entity.FmsAlertTemplate;
import cn.geelato.alert.support.SessionCtxHelper;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息模板服务：模板分页 / 按编码详情（全渠道变体 + 引用规则）/ 变体保存（UK code+channel）/
 * 试渲染（dry-run 占位）/ 上下文字典 / 历史记录构造示例上下文。
 *
 * <p>试渲染的真实沙箱（js 脚本 {@code $ctx}/{@code $tpl} 执行）留待后续，返回结构完整占位。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §3
 */
@Service
public class AlertTemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<cn.geelato.script.core.ScriptEngine> scriptEngineProvider;

    /** 模板分页（按 编码/渠道/上下文形态/启停 筛选）。 */
    public Map<String, Object> pageQuery(TemplateQueryParams params) {
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
        if (notBlank(params.templateCode)) {
            filters.add(Filter.eq("templateCode", params.templateCode.trim()));
        }
        if (notBlank(params.channel)) {
            filters.add(Filter.eq("channel", params.channel.trim()));
        }
        if (notBlank(params.contextType)) {
            filters.add(Filter.eq("contextType", params.contextType.trim().toUpperCase()));
        }
        if (params.enableStatus != null) {
            filters.add(Filter.eq("enableStatus", params.enableStatus));
        }
        if (notBlank(params.keyword)) {
            filters.add(Filter.like("templateName", "%" + params.keyword.trim() + "%"));
        }
        MetaQuery query = MetaFactory.query(FmsAlertTemplate.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.asc("templateCode"), Order.asc("channel"));
        PageResult<Map<String, Object>> pageResult = query.page(page, pageSize).page();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : pageResult.getRecords()) {
            items.add(toRowMini(rowToEntity(row)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /** 模板详情：该 code 全部渠道变体 + 引用它的规则清单（限定当前应用）。 */
    public Map<String, Object> detailByCode(String templateCode, String appId) {
        List<FmsAlertTemplate> variants = listVariants(templateCode, appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateCode", templateCode);
        List<Map<String, Object>> variantRows = new ArrayList<>();
        for (FmsAlertTemplate v : variants) {
            Map<String, Object> row = toRowMini(v);
            row.put("titleScript", v.getTitleScript());
            row.put("contentScript", v.getContentScript());
            row.put("contextSample", parseJsonObjectOrNull(v.getContextSampleJson()));
            row.put("definition", buildVariantNode(v));
            variantRows.add(row);
        }
        result.put("variants", variantRows);
        result.put("referencedByRules", findReferencingRules(templateCode, appId));
        return result;
    }

    /** 保存模板变体（UK: template_code+channel；文档实质变更 version 递增；parseOnly 预检留后续）。 */
    public FmsAlertTemplate save(TemplateSaveRequest req) {
        JsonNode doc = req.getDocument();
        if (doc == null || doc.isNull()) {
            throw new IllegalArgumentException("模板文档不能为空");
        }
        String templateCode = textAt(doc, "metadata", "code");
        if (!notBlank(templateCode)) {
            throw new IllegalArgumentException("metadata.code 不能为空");
        }
        if (!notBlank(req.getAppId())) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        // 变体清单：spec.variants[]（channel/title/content）；缺省单变体 default
        List<JsonNode> variantNodes = new ArrayList<>();
        JsonNode variants = doc.path("spec").path("variants");
        if (variants.isArray() && !variants.isEmpty()) {
            variants.forEach(variantNodes::add);
        } else {
            variantNodes.add(doc);
        }
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        String userName = SessionCtxHelper.getCurrentUserName();
        FmsAlertTemplate last = null;
        for (JsonNode vnode : variantNodes) {
            String channel = orDefault(text(vnode, "channel"), "default");
            FmsAlertTemplate existing = getVariant(templateCode, channel, req.getAppId());
            FmsAlertTemplate tpl = existing != null ? existing : new FmsAlertTemplate();
            // 版本递增：与库内列重建的变体节点比对（两侧同经 applyVariant+buildVariantNode 规范化）
            if (existing != null) {
                FmsAlertTemplate incoming = new FmsAlertTemplate();
                applyVariant(incoming, doc, vnode);
                boolean changed = definitionChanged(buildVariantNode(existing), buildVariantNode(incoming));
                tpl.setVersion(changed ? existing.getVersion() + 1 : existing.getVersion());
            } else {
                tpl.setVersion(1);
            }
            applyVariant(tpl, doc, vnode);
            if (req.getEnableStatus() != null) {
                tpl.setEnableStatus(req.getEnableStatus());
            }
            tpl.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
            tpl.setAppId(req.getAppId().trim());
            tpl.setUpdateAt(now);
            tpl.setUpdater(userId);
            tpl.setUpdaterName(userName);
            if (existing == null) {
                tpl.setDelStatus(0);
                tpl.setCreateAt(now);
                tpl.setCreator(userId);
                tpl.setCreatorName(userName);
                if (req.getEnableStatus() == null) {
                    tpl.setEnableStatus(1);
                }
            }
            String savedId = MetaFactory.save(tpl);
            if (tpl.getId() == null && savedId != null) {
                tpl.setId(savedId);
            }
            last = tpl;
        }
        return last;
    }

    /** 按 templateCode+channel 取变体（当前租户 + 指定应用）；不存在或跨应用返回 null。 */
    public FmsAlertTemplate getVariant(String templateCode, String channel, String appId) {
        if (!notBlank(appId)) {
            return null;
        }
        MetaQuery query = MetaFactory.query(FmsAlertTemplate.class)
                .where(Filter.eq("templateCode", templateCode),
                        Filter.eq("channel", channel),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 某编码的全部渠道变体（限定当前应用）；appId 为空返回空集。 */
    public List<FmsAlertTemplate> listVariants(String templateCode, String appId) {
        List<FmsAlertTemplate> list = new ArrayList<>();
        if (!notBlank(appId)) {
            return list;
        }
        MetaQuery query = MetaFactory.query(FmsAlertTemplate.class)
                .where(Filter.eq("templateCode", templateCode),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("channel"));
        List<Map<String, Object>> rows = query.list();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                list.add(rowToEntity(row));
            }
        }
        return list;
    }

    /**
     * 试渲染（{@code POST /test-render}）：渲染引擎可用时按变体真实执行 title/content 脚本
     * （alert-render profile：$ctx/$tpl 注入、2s 超时、sql/http 结构性不可达、不落库不投递），
     * title≤120 / content≤32KB 超长截断告警；不可用时返回占位变体清单。
     *
     * @return {@code {renders:[{channel,title,content,costMs,console,executed,engineVersion}]}}
     */
    public Map<String, Object> testRender(String templateCode, JsonNode context, String appId) {
        cn.geelato.script.core.ScriptEngine engine = scriptEngineProvider == null
                ? null : scriptEngineProvider.getIfAvailable();
        Map<String, Object> ctxBindings = context == null || context.isNull()
                ? Map.of()
                : MAPPER.convertValue(context, Map.class);
        List<Map<String, Object>> renders = new ArrayList<>();
        for (FmsAlertTemplate v : listVariants(templateCode, appId)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channel", v.getChannel());
            if (engine != null) {
                r.putAll(renderVariant(engine, v, ctxBindings));
            } else {
                r.put("title", null);
                r.put("content", null);
                r.put("costMs", 0);
                r.put("console", List.of("渲染沙箱未启用，未执行"));
                r.put("executed", false);
            }
            renders.add(r);
        }
        if (renders.isEmpty()) {
            throw new IllegalArgumentException("模板变体不存在: " + templateCode);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateCode", templateCode);
        result.put("contextAccepted", context != null && !context.isNull());
        result.put("renders", renders);
        if (engine == null) {
            result.put("note", "渲染引擎未启用，本结果仅变体清单占位");
        }
        return result;
    }

    private Map<String, Object> renderVariant(cn.geelato.script.core.ScriptEngine engine,
                                              FmsAlertTemplate variant, Map<String, Object> ctxBindings) {
        Map<String, Object> out = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        List<String> console = new ArrayList<>();
        String title = null;
        String content = null;
        String error = null;
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("$ctx", ctxBindings);
        cn.geelato.script.core.ScriptResult titleResult = engine.execute(cn.geelato.script.core.ScriptExecution.builder()
                .content(variant.getTitleScript()).scriptName(variant.getTemplateCode() + ":title")
                .profileId("alert-render")
                .engineVersion(variant.getEngineVersion()).bindings(bindings).build());
        console.addAll(titleResult.getConsole() == null ? List.of() : titleResult.getConsole());
        cn.geelato.script.core.ScriptResult contentResult = engine.execute(cn.geelato.script.core.ScriptExecution.builder()
                .content(variant.getContentScript()).scriptName(variant.getTemplateCode() + ":content")
                .profileId("alert-render")
                .engineVersion(variant.getEngineVersion()).bindings(bindings).build());
        console.addAll(contentResult.getConsole() == null ? List.of() : contentResult.getConsole());
        if ("SUCCESS".equals(titleResult.getStatus()) && titleResult.getValue() != null) {
            title = String.valueOf(titleResult.getValue());
        } else if (!"SUCCESS".equals(titleResult.getStatus()) && titleResult.getError() != null) {
            error = "title: " + titleResult.getError().getType() + " " + titleResult.getError().getMessage();
        }
        if ("SUCCESS".equals(contentResult.getStatus()) && contentResult.getValue() != null) {
            content = String.valueOf(contentResult.getValue());
        } else if (!"SUCCESS".equals(contentResult.getStatus()) && contentResult.getError() != null) {
            error = (error == null ? "" : error + "; ") + "content: "
                    + contentResult.getError().getType() + " " + contentResult.getError().getMessage();
        }
        if (title != null && title.length() > 120) {
            title = title.substring(0, 120);
            console.add("[warn] title 超过 120 字符已截断");
        }
        if (content != null && content.length() > 32_768) {
            content = content.substring(0, 32_768);
            console.add("[warn] content 超过 32KB 已截断");
        }
        out.put("title", title);
        out.put("content", content);
        out.put("costMs", System.currentTimeMillis() - start);
        out.put("console", console);
        out.put("executed", true);
        out.put("engineVersion", contentResult.getEngineVersion());
        if (error != null) {
            out.put("error", error);
        }
        return out;
    }

    /**
     * 上下文字典（配置页速查面板数据源）：SINGLE/GROUP/SCHEDULE 三形态字段树静态字典。
     *
     * <p>字段口径对齐《消息模板设计》§2 渲染上下文契约。
     */
    public Map<String, Object> contextSchema(String type) {
        String t = type == null ? "single" : type.toLowerCase();
        List<Map<String, Object>> fields = new ArrayList<>();
        switch (t) {
            case "group" -> {
                fields.add(field("group", "object", "聚合组信息"));
                fields.add(field("group.alertCode", "string", "告警规则编码"));
                fields.add(field("group.groupKey", "string", "聚合身份键"));
                fields.add(field("group.stats", "object", "聚合统计（byLevel/byDomain/total）"));
                fields.add(field("group.items", "array<exception>", "命中异常清单（maxItems 截断）"));
                fields.add(field("group.firstEventAt", "datetime", "组内首事件时刻"));
                fields.add(field("group.lastEventAt", "datetime", "组内末事件时刻"));
            }
            case "schedule" -> {
                fields.add(field("digest", "object", "定时数据集结果"));
                fields.add(field("digest.kind", "string", "数据集口径（UNRESOLVED_DIGEST/TODAY_DIGEST/SLA_OVER/STATS_THRESHOLD）"));
                fields.add(field("digest.conditionMet", "boolean", "发送条件是否满足"));
                fields.add(field("digest.stats", "object", "统计指标"));
                fields.add(field("digest.items", "array<exception>", "数据集清单行"));
            }
            default -> {
                fields.add(field("exception", "object", "命中异常实例（platform_exception 行）"));
                fields.add(field("exception.id", "string", "异常实例 id"));
                fields.add(field("exception.ruleCode", "string", "异常规则编码"));
                fields.add(field("exception.ruleName", "string", "异常规则名称"));
                fields.add(field("exception.domainCode", "string", "业务域编码"));
                fields.add(field("exception.level", "string", "等级 P0~P3"));
                fields.add(field("exception.customerName", "string", "客户名称"));
                fields.add(field("exception.bizNo", "string", "业务单号"));
                fields.add(field("exception.participantName", "string", "处理人"));
                fields.add(field("exception.firstAt", "datetime", "首次触发"));
                fields.add(field("exception.anchorElapsed", "number", "锚点滞留秒"));
                fields.add(field("exception.hitBrief", "string", "命中摘要"));
                fields.add(field("exception.detail", "object", "触发快照"));
                fields.add(field("eventType", "string", "触发的异常生命周期事件"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", t);
        result.put("fields", fields);
        result.put("helpers", List.of("$tpl.levelTag", "$tpl.domainName", "$tpl.fmtTime",
                "$tpl.fmtDuration", "$tpl.fmtAmount", "$tpl.workbenchUrl"));
        return result;
    }

    // ==================== 内部辅助 ====================

    /** 引用该模板编码的规则清单（限定当前应用；按通道路由列 route_json 粗筛）。 */
    private List<Map<String, Object>> findReferencingRules(String templateCode, String appId) {
        if (!notBlank(appId)) {
            return new ArrayList<>();
        }
        MetaQuery query = MetaFactory.query(FmsAlertRule.class)
                .where(Filter.like("routeJson", "%" + templateCode + "%"),
                        Filter.eq("appId", appId.trim()),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> refs = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("id", str(row.get("id")));
                ref.put("alertCode", str(row.get("alertCode")));
                ref.put("alertName", str(row.get("alertName")));
                ref.put("enableStatus", intVal(row.get("enableStatus")));
                refs.add(ref);
            }
        }
        return refs;
    }

    private Map<String, Object> field(String path, String type, String desc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", path);
        map.put("type", type);
        map.put("desc", desc);
        return map;
    }

    private Map<String, Object> toRowMini(FmsAlertTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tpl.getId());
        map.put("templateCode", tpl.getTemplateCode());
        map.put("templateName", tpl.getTemplateName());
        map.put("templateDesc", tpl.getTemplateDesc());
        map.put("channel", tpl.getChannel());
        map.put("contextType", tpl.getContextType());
        map.put("enableStatus", tpl.getEnableStatus());
        map.put("version", tpl.getVersion());
        return map;
    }

    /** 变体实质变更判定：两侧均为规范化（applyVariant + buildVariantNode）后的节点，顺序无关比较。 */
    private boolean definitionChanged(JsonNode oldNode, JsonNode newNode) {
        return oldNode == null || !oldNode.equals(newNode);
    }

    /** 文档 → 变体规范化列（apiVersion 归一为短版本存 engine_version）。 */
    private void applyVariant(FmsAlertTemplate tpl, JsonNode doc, JsonNode vnode) {
        tpl.setTemplateCode(textAt(doc, "metadata", "code"));
        tpl.setTemplateName(orEmpty(textAt(doc, "metadata", "name")));
        tpl.setTemplateDesc(orEmpty(textAt(doc, "metadata", "desc")));
        tpl.setChannel(orDefault(text(vnode, "channel"), "default"));
        tpl.setTitleScript(orEmpty(text(vnode, "title")));
        tpl.setContentScript(orEmpty(text(vnode, "content")));
        tpl.setContextType(orDefault(textAt(doc, "spec", "contextType"), "SINGLE").toUpperCase());
        tpl.setContextSampleJson(writeJsonAt(vnode.path("contextSample")));
        tpl.setEngineVersion(normalizeEngineVersion(text(doc, "apiVersion")));
    }

    /** 变体规范化列 → 编辑器变体节点（detail definition 输出的统一来源）。 */
    private JsonNode buildVariantNode(FmsAlertTemplate tpl) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("channel", tpl.getChannel());
        node.put("title", tpl.getTitleScript());
        node.put("content", tpl.getContentScript());
        JsonNode sample = parseDocumentOrNull(tpl.getContextSampleJson());
        if (sample != null) {
            node.put("contextSample", sample);
        }
        return MAPPER.valueToTree(node);
    }

    /** apiVersion（如 alert.geelato/v1）→ 短版本（v1），适配 engine_version varchar(8)。 */
    private String normalizeEngineVersion(String apiVersion) {
        if (!notBlank(apiVersion)) {
            return null;
        }
        int idx = apiVersion.lastIndexOf('/');
        String v = idx >= 0 ? apiVersion.substring(idx + 1) : apiVersion;
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > 8 ? v.substring(0, 8) : v;
    }

    private FmsAlertTemplate rowToEntity(Map<String, Object> row) {
        FmsAlertTemplate tpl = new FmsAlertTemplate();
        tpl.setId(str(row.get("id")));
        tpl.setTemplateCode(str(row.get("templateCode")));
        tpl.setTemplateName(str(row.get("templateName")));
        tpl.setTemplateDesc(str(row.get("templateDesc")));
        tpl.setChannel(str(row.get("channel")));
        tpl.setTitleScript(str(row.get("titleScript")));
        tpl.setContentScript(str(row.get("contentScript")));
        tpl.setContextSampleJson(str(row.get("contextSampleJson")));
        tpl.setContextType(str(row.get("contextType")));
        tpl.setEngineVersion(str(row.get("engineVersion")));
        tpl.setVersion(intVal(row.get("version")));
        tpl.setEnableStatus(intVal(row.get("enableStatus")));
        tpl.setUpdateAt(toDate(row.get("updateAt")));
        tpl.setAppId(str(row.get("appId")));
        tpl.setTenantCode(str(row.get("tenantCode")));
        tpl.setDelStatus(intVal(row.get("delStatus")));
        return tpl;
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

    private String orEmpty(String s) {
        return s == null ? "" : s;
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

    /** 模板分页查询参数。 */
    public static class TemplateQueryParams {
        public String appId;
        public String templateCode;
        public String channel;
        public String contextType;
        public Integer enableStatus;
        public String keyword;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 模板保存请求（{@code POST /template/save}）。 */
    @lombok.Data
    public static class TemplateSaveRequest {
        /** 编辑器 TemplateDocument 原文（metadata + spec.variants[]）。 */
        private JsonNode document;
        /** 所属应用ID（platform_app.id），新增必填、编辑须与原归属一致。 */
        private String appId;
        private Integer enableStatus;
    }

    /** 试渲染请求。 */
    @lombok.Data
    public static class TestRenderRequest {
        public String templateCode;
        public String appId;
        public JsonNode context;
    }
}
