package cn.geelato.exception.engine;

import cn.geelato.exception.engine.hit.HitPolicies;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.engine.scan.RuleCronCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitPoliciesTest {

    @Test
    void suppressScopes() {
        Map<String, Object> hit = Map.of("bizType", "BOL_GRANT", "bizId", "1", "customerId", "C9", "ownerOrgId", "ORG7");
        assertTrue(HitPolicies.suppressedBy(hit, "BIZ", Set.of("BOL_GRANT|1")));
        assertFalse(HitPolicies.suppressedBy(hit, "BIZ", Set.of("BOL_GRANT|2")));
        assertTrue(HitPolicies.suppressedBy(hit, "CUSTOMER", Set.of("C9")));
        assertTrue(HitPolicies.suppressedBy(hit, "ORG", Set.of("ORG7")));
        assertFalse(HitPolicies.suppressedBy(hit, "CUSTOMER", Set.of()));
    }

    @Test
    void hitLimitTruncates() {
        List<Map<String, Object>> hits = List.of(Map.of("a", 1), Map.of("a", 2), Map.of("a", 3));
        HitPolicies.Overflow unlimited = HitPolicies.applyHitLimit(hits, null);
        assertEquals(3, unlimited.kept().size());
        assertEquals(0, unlimited.dropped());
        HitPolicies.Overflow limited = HitPolicies.applyHitLimit(hits, 2);
        assertEquals(2, limited.kept().size());
        assertEquals(1, limited.dropped());
    }

    @Test
    void cooldownWindow() {
        long now = System.currentTimeMillis();
        assertTrue(HitPolicies.inCooldown(now - 10 * 60_000L, 30, now));
        assertFalse(HitPolicies.inCooldown(now - 31 * 60_000L, 30, now));
        assertFalse(HitPolicies.inCooldown(null, 30, now));
        assertFalse(HitPolicies.inCooldown(now - 60_000L, null, now));
    }

    @Test
    void sourceKeysDistinct() {
        String ruleCode = "EXC-BL-005";
        List<Map<String, Object>> hits = List.of(
                Map.of("bizType", "B", "bizId", "1"),
                Map.of("bizType", "B", "bizId", "1"),
                Map.of("bizType", "B", "bizId", "2"));
        assertEquals(Set.of("EXC-BL-005|B|1", "EXC-BL-005|B|2"), HitPolicies.distinctSourceKeys(ruleCode, hits));
    }

    @Test
    void cronDefaultsByLevel() {
        FmsExceptionRule p0 = new FmsExceptionRule();
        p0.setLevel("P0");
        assertTrue(RuleCronCalculator.cronOf(p0).contains("*/15"));
        FmsExceptionRule custom = new FmsExceptionRule();
        custom.setLevel("P1");
        custom.setCronExpression("0 0 8 * * *");
        assertEquals("0 0 8 * * *", RuleCronCalculator.cronOf(custom));
    }

    @Test
    void dueDetectionAndMissedFireOnce() {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setLevel("P1");
        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0, 0);
        // 首跑：无历史，立即可跑
        assertTrue(RuleCronCalculator.isDue(rule, null, now));
        // 上轮 11:30，P1 每小时 → 12:00 到期
        assertTrue(RuleCronCalculator.isDue(rule, LocalDateTime.of(2026, 9, 29, 11, 30, 0), now));
        // 上轮 12:00，下一次 13:00 未到（now=12:30）
        assertFalse(RuleCronCalculator.isDue(rule, LocalDateTime.of(2026, 9, 29, 12, 0, 0), now.plusMinutes(30)));
        // missed fire：上轮是三天前，next 早过 now，仍只触发一次（语义：补跑一次）
        assertTrue(RuleCronCalculator.isDue(rule, LocalDateTime.of(2026, 9, 26, 0, 0, 0), now));
    }
}
