package cn.geelato.alert.engine.render;

import cn.geelato.alert.engine.SourceSnapshots;
import cn.geelato.alert.engine.deliver.DeliverException;
import cn.geelato.core.orm.Dao;
import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.script.core.ScriptEngine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染器：$ctx 三形态构建（SINGLE=$source 源实例快照 / GROUP=清单+统计 / SCHEDULE=数据集注入）
 * + 变体查找（(code,channel) 停用或缺失回退 (code,default)）+ 脚本执行。
 * 源业务数据一律取自告警记录 source_json（落库时采集，告警与源模块解耦，渲染不回查源表）。
 * 模板字典对齐 bea/业务告警中心-消息模板设计-2026-09-22.md §3。
 */
@Slf4j
public class AlertRenderer {

    private final Dao dao;
    private final ScriptEngine scriptEngine;

    public AlertRenderer(Dao dao, ScriptEngine scriptEngine) {
        this.dao = dao;
        this.scriptEngine = scriptEngine;
    }

    /** 模板变体引用（titleScript/contentScript/engineVersion），渠道回退后的实际变体。 */
    @Getter
    public static class VariantRef {
        private final String channel;
        private final String titleScript;
        private final String contentScript;
        private final String engineVersion;

        public VariantRef(String channel, String titleScript, String contentScript, String engineVersion) {
            this.channel = channel;
            this.titleScript = titleScript;
            this.contentScript = contentScript;
            this.engineVersion = engineVersion;
        }
    }

    /**
     * 变体查找：(templateCode, channel) 启用行；缺失/停用回退 (templateCode, default)；再无返回 null。
     */
    public VariantRef resolveVariant(String templateCode, String channel, String appId, String tenantCode) {
        VariantRef exact = queryVariant(templateCode, channel, appId, tenantCode);
        if (exact != null) {
            return exact;
        }
        return queryVariant(templateCode, "default", appId, tenantCode);
    }

    /**
     * 渲染主快照（default 变体）：冲刷时落 rendered_title/rendered_content/stats_json。
     *
     * @param extraCtx schedule 形态注入 dataset 段（DatasetExecutor 产出）；其他形态可空
     */
    public Snapshot renderSnapshot(FmsAlert record, FmsAlertRule rule, Map<String, Object> extraCtx) {
        VariantRef variant = resolveVariant(templateCodeOf(rule), "default", rule.getAppId(), record.getTenantCode());
        Map<String, Object> ctx = buildContext(record, rule, extraCtx);
        VariantScripts.RenderResult result = VariantScripts.run(scriptEngine,
                codePrefixed(rule, variant), variant.getEngineVersion(),
                variant.getTitleScript(), variant.getContentScript(), ctx);
        if (result.getError() != null) {
            throw new DeliverException("模板渲染失败 [" + variant.getChannel() + "]: " + result.getError());
        }
        return new Snapshot(result.getTitle(), result.getContent(), extractStats(ctx, extraCtx));
    }

    /** 快照三件套：渲染标题/正文 + 聚合统计（group 形态=group.stats；schedule=dataset 整体；single=null）。 */
    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final String title;
        private final String content;
        private final Object stats;
    }

    private static Object extractStats(Map<String, Object> ctx, Map<String, Object> extraCtx) {
        Map<?, ?> group = (Map<?, ?>) ctx.get("group");
        if (group != null) {
            return group.get("stats");
        }
        return extraCtx == null ? null : extraCtx.get("dataset");
    }

    /**
     * 渠道变体渲染（投递时逐渠道）：无专属变体返回 null（调用方复用主快照），不重复执行 default 脚本。
     */
    public VariantScripts.RenderResult renderChannel(FmsAlert record, FmsAlertRule rule, String channel) {
        String code = templateCodeOf(rule);
        VariantRef exact = queryVariant(code, channel, rule.getAppId(), record.getTenantCode());
        if (exact == null || "default".equals(channel)) {
            return null;
        }
        return render(record, rule, exact, null);
    }

    /** 执行变体脚本；脚本抛错/超时抛 DeliverException（渲染失败 → 记录 FAIL）。 */
    public VariantScripts.RenderResult render(FmsAlert record, FmsAlertRule rule,
                                              VariantRef variant, Map<String, Object> extraCtx) {
        if (variant == null) {
            throw new DeliverException("模板变体不存在: " + templateCodeOf(rule));
        }
        Map<String, Object> ctx = buildContext(record, rule, extraCtx);
        VariantScripts.RenderResult result = VariantScripts.run(scriptEngine,
                codePrefixed(rule, variant), variant.getEngineVersion(),
                variant.getTitleScript(), variant.getContentScript(), ctx);
        if (result.getError() != null) {
            throw new DeliverException("模板渲染失败 [" + variant.getChannel() + "]: " + result.getError());
        }
        return result;
    }

    /**
     * $ctx 构建。形态判定：SCHEDULE→schedule（extraCtx.dataset）；REPEAT 或 EVENT×AGGREGATE→group；
     * 其余（EVENT×INDEPENDENT / RESOLVE）→single（source 段由 VariantScripts 提升为一级绑定 $source）。
     */
    public Map<String, Object> buildContext(FmsAlert record, FmsAlertRule rule, Map<String, Object> extraCtx) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> ruleSeg = new LinkedHashMap<>();
        ruleSeg.put("alertCode", rule.getAlertCode());
        ruleSeg.put("alertName", rule.getAlertName());
        ruleSeg.put("triggerType", record.getTriggerType());
        ruleSeg.put("eventType", record.getEventType());
        ruleSeg.put("time", SourceSnapshots.fmtTime(new java.util.Date()));
        ruleSeg.put("maxItems", maxItems(rule));
        ctx.put("rule", ruleSeg);

        String shape = contextShape(record, rule);
        switch (shape) {
            case "schedule" -> ctx.put("dataset", extraCtx == null ? null : extraCtx.get("dataset"));
            case "group" -> ctx.putAll(buildGroupSegment(record, rule));
            default -> ctx.put("source", buildSourceSegment(record));
        }
        return ctx;
    }

    /** 形态判定（single/group/schedule）。 */
    public static String contextShape(FmsAlert record, FmsAlertRule rule) {
        if ("SCHEDULE".equals(record.getTriggerType())) {
            return "schedule";
        }
        if ("REPEAT".equals(record.getTriggerType())
                || ("EVENT".equals(record.getTriggerType()) && "AGGREGATE".equals(rule.getAggregateMode()))) {
            return "group";
        }
        return "single";
    }

    // ==================== single：$source = 告警落库时采集的源实例快照 ====================

    private Map<String, Object> buildSourceSegment(FmsAlert record) {
        Map<String, Object> source;
        try {
            source = SourceSnapshots.parseItem(record.getSourceJson());
        } catch (IllegalStateException e) {
            throw new DeliverException("source_json 解析失败（single 形态）: record=" + record.getId()
                    + ", " + e.getMessage());
        }
        if (source == null) {
            throw new DeliverException("source_json 缺失（single 形态，告警落库时未采集源数据）: record=" + record.getId());
        }
        return source;
    }

    // ==================== group：清单 + 统计（快照数组截断） ====================

    private Map<String, Object> buildGroupSegment(FmsAlert record, FmsAlertRule rule) {
        List<Map<String, Object>> items;
        try {
            items = SourceSnapshots.parseItems(record.getSourceJson());
        } catch (IllegalStateException e) {
            throw new DeliverException("source_json 解析失败（group 形态）: record=" + record.getId()
                    + ", " + e.getMessage());
        }
        if (items.isEmpty()) {
            throw new DeliverException("source_json 缺失（group 形态，告警落库时未采集源数据）: record=" + record.getId());
        }
        Map<String, Integer> byRule = new LinkedHashMap<>();
        double amountTotal = 0;
        boolean amountSeen = false;
        for (Map<String, Object> item : items) {
            byRule.merge(SourceSnapshots.str(item.get("ruleCode")), 1, Integer::sum);
            Double amount = SourceSnapshots.amountOf(item);
            if (amount != null) {
                amountTotal += amount;
                amountSeen = true;
            }
        }
        int maxItems = maxItems(rule);
        boolean truncated = items.size() > maxItems;
        List<Map<String, Object>> alerts = truncated
                ? new ArrayList<>(items.subList(0, maxItems)) : items;

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("key", record.getGroupKey());
        group.put("firstEventAt", SourceSnapshots.fmtTime(record.getFirstEventAt()));
        group.put("lastEventAt", SourceSnapshots.fmtTime(record.getLastEventAt()));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", items.size());
        stats.put("byRule", byRule);
        stats.put("amountTotal", amountSeen ? amountTotal : null);
        group.put("stats", stats);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("group", group);
        out.put("alerts", alerts);
        out.put("alertsTruncated", truncated);
        return out;
    }

    // ==================== 内部辅助 ====================

    private VariantRef queryVariant(String templateCode, String channel, String appId, String tenantCode) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT title_script, content_script, engine_version FROM platform_alert_template "
                        + "WHERE template_code = ? AND channel = ? AND app_id = ? AND tenant_code = ? "
                        + "AND del_status = 0 AND enable_status = 1",
                new Object[]{templateCode, channel, appId, tenantCode});
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new VariantRef(channel, str(pick(row, "title_script", "titleScript")),
                str(pick(row, "content_script", "contentScript")),
                str(pick(row, "engine_version", "engineVersion")));
    }

    private static String templateCodeOf(FmsAlertRule rule) {
        String code = cn.geelato.alert.engine.deliver.AlertRoute.templateCode(rule.getRouteJson());
        if (code == null) {
            throw new DeliverException("规则缺路由模板编码 route.default.templateCode: " + rule.getAlertCode());
        }
        return code;
    }

    private static String codePrefixed(FmsAlertRule rule, VariantRef variant) {
        return templateCodeOf(rule) + ":" + variant.getChannel();
    }

    private static int maxItems(FmsAlertRule rule) {
        return rule.getMaxItems() == null || rule.getMaxItems() <= 0 ? 20 : rule.getMaxItems();
    }

    private static Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
