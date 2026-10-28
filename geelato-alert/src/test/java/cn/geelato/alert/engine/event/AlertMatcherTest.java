package cn.geelato.alert.engine.event;

import cn.geelato.alert.entity.FmsAlertRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMatcherTest {

    private FmsAlertRule rule(String matchJson) {
        FmsAlertRule rule = new FmsAlertRule();
        rule.setAlertCode("ALERT-001");
        rule.setMatchJson(matchJson);
        return rule;
    }

    @Test
    void matchesByEventType() {
        FmsAlertRule rule = rule("{\"source\":\"EXCEPTION\",\"events\":[\"EXC_CREATED\",\"EXC_ESCALATED\"]}");
        assertTrue(AlertMatcher.match(rule, "EXC_CREATED", "EXC-FIN-001"));
        assertTrue(AlertMatcher.match(rule, "EXC_ESCALATED", null));
        assertFalse(AlertMatcher.match(rule, "EXC_RECOVERED", "EXC-FIN-001"));
    }

    @Test
    void ruleCodesDeclaredMustContain() {
        FmsAlertRule rule = rule("{\"source\":\"EXCEPTION\",\"events\":[\"EXC_CREATED\"],\"ruleCodes\":[\"EXC-FIN-001\"]}");
        assertTrue(AlertMatcher.match(rule, "EXC_CREATED", "EXC-FIN-001"));
        assertFalse(AlertMatcher.match(rule, "EXC_CREATED", "EXC-OPS-002"));
        assertFalse(AlertMatcher.match(rule, "EXC_CREATED", null));
    }

    @Test
    void ruleCodesEmptyMatchesAnySourceRule() {
        FmsAlertRule rule = rule("{\"source\":\"EXCEPTION\",\"events\":[\"EXC_CREATED\"]}");
        assertTrue(AlertMatcher.match(rule, "EXC_CREATED", "ANY-RULE"));
        assertTrue(AlertMatcher.match(rule, "EXC_CREATED", null));
    }

    @Test
    void unknownSourceOrMalformedJsonNeverMatches() {
        assertFalse(AlertMatcher.match(rule("{\"source\":\"SCHEDULE\",\"events\":[\"EXC_CREATED\"]}"), "EXC_CREATED", null));
        assertFalse(AlertMatcher.match(rule("not-a-json"), "EXC_CREATED", null));
        assertFalse(AlertMatcher.match(rule(null), "EXC_CREATED", null));
    }
}
