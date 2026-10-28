package cn.geelato.alert.engine.dataset;

import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.core.orm.Dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCHEDULE 数据集执行器（规则 YAML 设计 §3.2）：四 kind 只读 platform_exception。
 * condition 缺省 COUNT GTE 1（digest 空清单不骚扰）；STATS_THRESHOLD 按 metric（OPEN_COUNT/
 * TODAY_NEW/CLOSE_RATE）×op×value 判定。rows 按 firstAt 正序截断 maxItems（缺省 20）。
 */
public class DatasetExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ACTIVE_STATUSES = "('OPEN','PROCESSING','ESCALATED')";

    private final Dao dao;

    public DatasetExecutor(@Qualifier("dynamicDao") Dao dao) {
        this.dao = dao;
    }

    /** 执行结果：conditionHit=false 时不落告警记录（不骚扰）。 */
    @Getter
    public static class Outcome {
        private final boolean conditionHit;
        private final Map<String, Object> dataset;

        public Outcome(boolean conditionHit, Map<String, Object> dataset) {
            this.conditionHit = conditionHit;
            this.dataset = dataset;
        }
    }

    public Outcome execute(FmsAlertRule rule) {
        JsonNode spec = parse(rule.getDatasetJson());
        String kind = textOf(spec == null ? null : spec.path("kind"), "UNRESOLVED_DIGEST");
        JsonNode filters = spec == null ? null : spec.path("filters");
        JsonNode condition = spec == null ? null : spec.path("condition");

        List<String> ruleCodes = textList(filters == null ? null : filters.path("ruleCodes"));
        long slaOverSeconds = slaOverSeconds(filters) * 3600L;
        int maxItems = rule.getMaxItems() == null || rule.getMaxItems() <= 0 ? 20 : rule.getMaxItems();

        String baseWhere = "tenant_code = ? AND del_status = 0 AND status IN " + ACTIVE_STATUSES;
        List<Object> baseArgs = new ArrayList<>(List.of(rule.getTenantCode()));
        if (!ruleCodes.isEmpty()) {
            baseWhere += " AND rule_code IN (" + placeholders(ruleCodes.size()) + ")";
            baseArgs.addAll(ruleCodes);
        }

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("kind", kind);
        boolean isToday = "TODAY_DIGEST".equals(kind);
        boolean isSla = "SLA_OVER".equals(kind);
        boolean isStats = "STATS_THRESHOLD".equals(kind);

        String where = baseWhere;
        List<Object> args = new ArrayList<>(baseArgs);
        if (isToday) {
            where += " AND first_at >= CURDATE()";
        }
        if (isSla) {
            where += " AND anchor_elapsed >= ?";
            args.add(slaOverSeconds);
        }
        dataset.put("timeWindow", timeWindow(isToday));

        long total = count(where, args);
        dataset.put("total", total);

        if (isStats) {
            dataset.put("rows", statsRows(baseWhere, baseArgs));
            dataset.put("stats", groupStats(baseWhere, baseArgs));
        } else {
            dataset.put("rows", listRows(where, args, maxItems));
            dataset.put("stats", groupStats(where, args));
        }

        boolean hit;
        Map<String, Object> conditionDetail = null;
        if (isStats) {
            String metric = textOf(condition == null ? null : condition.path("metric"), "OPEN_COUNT");
            String op = textOf(condition == null ? null : condition.path("op"), "GTE");
            double value = condition == null || condition.path("value").isMissingNode()
                    ? 1 : condition.path("value").asDouble(1);
            double actual = metricValue(metric, baseWhere, baseArgs);
            hit = compare(actual, op, value);
            conditionDetail = new LinkedHashMap<>();
            conditionDetail.put("metric", metric);
            conditionDetail.put("op", op);
            conditionDetail.put("value", value);
            conditionDetail.put("actual", actual);
        } else {
            // digest/sla：COUNT GTE 1 缺省（空清单不骚扰），显式 condition 可覆盖
            String op = textOf(condition == null ? null : condition.path("op"), "GTE");
            double value = condition == null || condition.path("value").isMissingNode()
                    ? 1 : condition.path("value").asDouble(1);
            hit = compare(total, op, value);
        }
        dataset.put("conditionHit", hit);
        if (conditionDetail != null) {
            dataset.put("conditionDetail", conditionDetail);
        }
        return new Outcome(hit, dataset);
    }

    // ==================== 内部辅助 ====================

    private long count(String where, List<Object> args) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT COUNT(*) AS cnt FROM platform_exception WHERE " + where, args.toArray());
        if (rows != null && !rows.isEmpty()) {
            Object v = rows.get(0).containsKey("cnt") ? rows.get(0).get("cnt") : rows.get(0).get("CNT");
            if (v instanceof Number n) {
                return n.longValue();
            }
        }
        return 0;
    }

    /** 清单行（异常精简版，同 group alerts 项口径）。 */
    private List<Map<String, Object>> listRows(String where, List<Object> args, int maxItems) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT id, rule_code, rule_name, domain_code, level, status, biz_label, "
                        + "participant_name, first_at, anchor_elapsed, title, detail_json "
                        + "FROM platform_exception WHERE " + where + " ORDER BY first_at ASC LIMIT " + maxItems,
                args.toArray());
        return rows == null ? List.of() : rows;
    }

    /** STATS_THRESHOLD 分组统计行 {dim, count, p0, p1}（按规则维度分组）。 */
    private List<Map<String, Object>> statsRows(String where, List<Object> args) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT rule_code AS dim, COUNT(*) AS count, "
                        + "SUM(CASE WHEN level = 'P0' THEN 1 ELSE 0 END) AS p0, "
                        + "SUM(CASE WHEN level = 'P1' THEN 1 ELSE 0 END) AS p1 "
                        + "FROM platform_exception WHERE " + where + " GROUP BY rule_code ORDER BY count DESC",
                args.toArray());
        return rows == null ? List.of() : rows;
    }

    /** 汇总统计（byLevel/byDomain/byRule 计数）。 */
    private Map<String, Object> groupStats(String where, List<Object> args) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("byLevel", groupCount("level", where, args));
        stats.put("byDomain", groupCount("domain_code", where, args));
        stats.put("byRule", groupCount("rule_code", where, args));
        return stats;
    }

    private Map<String, Object> groupCount(String column, String where, List<Object> args) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT " + column + " AS dim, COUNT(*) AS count FROM platform_exception "
                        + "WHERE " + where + " GROUP BY " + column, args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String dim = String.valueOf(row.get("dim"));
                Object count = row.get("count");
                out.put("null".equals(dim) ? "UNKNOWN" : dim, count instanceof Number n ? n.longValue() : 0);
            }
        }
        return out;
    }

    private double metricValue(String metric, String where, List<Object> args) {
        switch (metric) {
            case "TODAY_NEW" -> {
                return count(where + " AND first_at >= CURDATE()", args);
            }
            case "CLOSE_RATE" -> {
                long active = count(where, args);
                long all = count(where.replace("status IN " + ACTIVE_STATUSES, "1=1"), args);
                return all == 0 ? 0 : (double) (all - active) / all;
            }
            default -> {
                return count(where, args);
            }
        }
    }

    private static boolean compare(double actual, String op, double value) {
        return switch (op == null ? "GTE" : op) {
            case "GT" -> actual > value;
            case "LTE" -> actual <= value;
            case "LT" -> actual < value;
            case "EQ" -> actual == value;
            default -> actual >= value;
        };
    }

    private static Map<String, Object> timeWindow(boolean today) {
        java.time.LocalDate todayDate = java.time.LocalDate.now();
        Map<String, Object> window = new LinkedHashMap<>();
        if (today) {
            window.put("from", todayDate.atStartOfDay().toString());
        } else {
            window.put("from", null);
        }
        window.put("to", java.time.LocalDateTime.now().toString());
        return window;
    }

    private static long slaOverSeconds(JsonNode filters) {
        if (filters == null) {
            return 24;
        }
        JsonNode v = filters.path("slaOverHours");
        if (v.isMissingNode() || v.isNull()) {
            return 24;
        }
        return Math.max(1, v.asLong(24));
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }

    private static List<String> textList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String v = n.asText(null);
                if (v != null && !v.isBlank()) {
                    out.add(v.trim());
                }
            }
        }
        return out;
    }

    private static String textOf(JsonNode node, String def) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return def;
        }
        return node.asText();
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
