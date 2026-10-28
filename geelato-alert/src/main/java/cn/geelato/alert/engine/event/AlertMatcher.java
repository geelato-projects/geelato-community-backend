package cn.geelato.alert.engine.event;

import cn.geelato.alert.engine.RowMappers;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.support.AlertSources;
import cn.geelato.core.orm.Dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 事件匹配（P-A1 ①）：事件类型命中 +（ruleCodes 声明则源规则编码包含）。
 * 匹配语义纯净化：客户/级别/业务对象等均为源实例属性，不进告警配置。
 */
public class AlertMatcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dao;

    public AlertMatcher(@Qualifier("dynamicDao") Dao dao) {
        this.dao = dao;
    }

    /** 加载租户内可参与事件匹配的启用规则（trigger_mode IN EVENT/BOTH）。 */
    public List<FmsAlertRule> loadEventRules(String tenantCode) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE enable_status = 1 AND del_status = 0 "
                        + "AND trigger_mode IN ('EVENT','BOTH') AND tenant_code = ?",
                new Object[]{tenantCode});
        List<FmsAlertRule> rules = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                rules.add(RowMappers.toRule(row));
            }
        }
        return rules;
    }

    /** match_json 匹配：source=EXCEPTION 且 events 含事件类型 且（ruleCodes 声明则包含源规则编码）。 */
    public static boolean match(FmsAlertRule rule, String eventType, String sourceRuleCode) {
        JsonNode match = parse(rule.getMatchJson());
        if (match == null || !AlertSources.EXCEPTION.equals(text(match.path("source")))) {
            return false;
        }
        JsonNode events = match.path("events");
        boolean eventHit = false;
        if (events.isArray()) {
            for (JsonNode e : events) {
                if (eventType != null && eventType.equals(e.asText())) {
                    eventHit = true;
                    break;
                }
            }
        }
        if (!eventHit) {
            return false;
        }
        JsonNode ruleCodes = match.path("ruleCodes");
        if (ruleCodes == null || !ruleCodes.isArray() || ruleCodes.isEmpty()) {
            return true;
        }
        for (JsonNode c : ruleCodes) {
            if (sourceRuleCode != null && sourceRuleCode.equals(c.asText())) {
                return true;
            }
        }
        return false;
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

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
