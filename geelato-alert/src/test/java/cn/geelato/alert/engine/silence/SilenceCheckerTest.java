package cn.geelato.alert.engine.silence;

import cn.geelato.alert.entity.FmsAlertSilence;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SilenceCheckerTest {

    private static Date at(String time) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(time);
    }

    private FmsAlertSilence silence(String matchJson, String start, String end, int allowResolve) throws Exception {
        FmsAlertSilence s = new FmsAlertSilence();
        s.setId("S1");
        s.setMatchJson(matchJson);
        s.setWindowType("ONCE");
        s.setStartAt(at(start));
        s.setEndAt(at(end));
        s.setAllowResolve(allowResolve);
        return s;
    }

    @Test
    void noDimensionMatchesAllAlerts() throws Exception {
        FmsAlertSilence s = silence(null, "2026-10-01 00:00:00", "2026-10-31 23:59:59", 0);
        assertEquals("S1", SilenceChecker.check(List.of(s), "ANY", "ANY", "ANY", "EVENT",
                at("2026-10-15 10:00:00")));
    }

    @Test
    void declaredDimensionMustContain() throws Exception {
        FmsAlertSilence s = silence("{\"alertCodes\":[\"ALERT-001\"],\"ruleCodes\":[\"EXC-FIN-001\"]}",
                "2026-10-01 00:00:00", "2026-10-31 23:59:59", 0);
        assertEquals("S1", SilenceChecker.check(List.of(s), "ALERT-001", "EXC-FIN-001", null, "EVENT",
                at("2026-10-15 10:00:00")));
        assertNull(SilenceChecker.check(List.of(s), "ALERT-002", "EXC-FIN-001", null, "EVENT",
                at("2026-10-15 10:00:00")));
        assertNull(SilenceChecker.check(List.of(s), "ALERT-001", "EXC-OPS-002", null, "EVENT",
                at("2026-10-15 10:00:00")));
    }

    @Test
    void outOfWindowNeverMatches() throws Exception {
        FmsAlertSilence s = silence(null, "2026-10-01 00:00:00", "2026-10-02 00:00:00", 0);
        assertNull(SilenceChecker.check(List.of(s), "ALERT-001", null, null, "EVENT",
                at("2026-10-15 10:00:00")));
    }

    @Test
    void resolveIsExemptedByAllowResolve() throws Exception {
        FmsAlertSilence allow = silence(null, "2026-10-01 00:00:00", "2026-10-31 23:59:59", 1);
        FmsAlertSilence block = silence(null, "2026-10-01 00:00:00", "2026-10-31 23:59:59", 0);
        assertNull(SilenceChecker.check(List.of(allow), "ALERT-001", null, null, "RESOLVE",
                at("2026-10-15 10:00:00")));
        assertEquals("S1", SilenceChecker.check(List.of(block), "ALERT-001", null, null, "RESOLVE",
                at("2026-10-15 10:00:00")));
    }

    @Test
    void recurringWindowChecksDayAndTimeRange() throws Exception {
        FmsAlertSilence s = new FmsAlertSilence();
        s.setId("S2");
        s.setWindowType("RECURRING");
        s.setStartAt(at("2026-10-01 00:00:00"));
        s.setEndAt(at("2026-10-31 23:59:59"));
        // 周一至周五 09:00-18:00（days: 1=周一..5=周五，0=周日）
        s.setTimeWindow("{\"days\":[1,2,3,4,5],\"startTime\":\"09:00\",\"endTime\":\"18:00\"}");
        // 2026-10-15 是周四 10:00 → 命中
        assertEquals("S2", SilenceChecker.check(List.of(s), "ALERT-001", null, null, "EVENT",
                at("2026-10-15 10:00:00")));
        // 同日凌晨（窗口外）→ 不命中
        assertNull(SilenceChecker.check(List.of(s), "ALERT-001", null, null, "EVENT",
                at("2026-10-15 08:00:00")));
        // 2026-10-17 周六 → 不命中
        assertNull(SilenceChecker.check(List.of(s), "ALERT-001", null, null, "EVENT",
                at("2026-10-17 10:00:00")));
    }
}
