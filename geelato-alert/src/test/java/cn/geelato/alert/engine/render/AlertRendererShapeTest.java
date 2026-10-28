package cn.geelato.alert.engine.render;

import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertRendererShapeTest {

    private FmsAlert record(String triggerType) {
        FmsAlert record = new FmsAlert();
        record.setTriggerType(triggerType);
        return record;
    }

    private FmsAlertRule rule(String aggregateMode) {
        FmsAlertRule rule = new FmsAlertRule();
        rule.setAlertCode("ALERT-001");
        rule.setAggregateMode(aggregateMode);
        return rule;
    }

    @Test
    void scheduleTriggerAlwaysScheduleShape() {
        assertEquals("schedule", AlertRenderer.contextShape(record("SCHEDULE"), rule("INDEPENDENT")));
        assertEquals("schedule", AlertRenderer.contextShape(record("SCHEDULE"), rule("AGGREGATE")));
    }

    @Test
    void repeatIsGroupShape() {
        assertEquals("group", AlertRenderer.contextShape(record("REPEAT"), rule("AGGREGATE")));
    }

    @Test
    void eventShapeFollowsAggregateMode() {
        assertEquals("group", AlertRenderer.contextShape(record("EVENT"), rule("AGGREGATE")));
        assertEquals("single", AlertRenderer.contextShape(record("EVENT"), rule("INDEPENDENT")));
    }

    @Test
    void resolveIsSingleShape() {
        assertEquals("single", AlertRenderer.contextShape(record("RESOLVE"), rule("AGGREGATE")));
        assertEquals("single", AlertRenderer.contextShape(record("RESOLVE"), rule("INDEPENDENT")));
    }
}
