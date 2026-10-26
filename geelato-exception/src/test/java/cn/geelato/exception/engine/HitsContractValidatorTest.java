package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.Hit;
import cn.geelato.exception.engine.core.HitsContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitsContractValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void checkStepExitDetectsMissingReturn() throws Exception {
        var steps = MAPPER.readTree("[{\"name\":\"judge\",\"lang\":\"js\",\"content\":\"const a = 1\"}]");
        assertFalse(HitsContractValidator.checkStepExit(steps).isEmpty());

        var ok = MAPPER.readTree("[{\"name\":\"judge\",\"lang\":\"js\",\"content\":\"return rows.filter(r => r.x > 0)\"}]");
        assertTrue(HitsContractValidator.checkStepExit(ok).isEmpty());

        var py = MAPPER.readTree("[{\"name\":\"calc\",\"lang\":\"python\",\"content\":\"__result__ = [1]\"}]");
        assertTrue(HitsContractValidator.checkStepExit(py).isEmpty());
    }

    @Test
    void runtimeRejectsNonArrayFatally() {
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime("not-a-list");
        assertTrue(check.fatal());
        assertEquals("HITS_NOT_ARRAY", check.violations().get(0).get("code"));
    }

    @Test
    void runtimeDropsInvalidHitsAndKeepsValid() {
        List<Object> hits = List.of(
                Map.of("bizType", "ORDER", "bizId", "1", "title", "t", "snapshot", Map.of("k", 1)),
                Map.of("bizType", "ORDER"),
                Map.of("bizId", "3", "title", "t", "snapshot", Map.of()),
                "garbage",
                Map.of("bizId", "5", "title", "t", "snapshot", Map.of(), "levelOverride", "P9"),
                Map.of("bizId", "6", "title", "t", "snapshot", Map.of(), "levelOverride", "P1"));
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(hits);
        assertFalse(check.fatal());
        assertEquals(4, check.validHits().size());
        assertEquals(2, check.violations().size());
        assertEquals("REQUIRED", check.violations().get(0).get("code"));
        assertEquals("HIT_NOT_OBJECT", check.violations().get(1).get("code"));
    }

    @Test
    void runtimeWarnsOnMissingTitleSnapshotAndBadAnchorTimeWithoutDropping() {
        List<Object> hits = List.of(
                Map.of("bizId", "1"),
                Map.of("bizId", "2", "anchorTime", "2026/10/22 09:30"),
                Map.of("bizId", "3", "title", "t", "snapshot", Map.of(), "anchorTime", "2026-10-22 09:30:15"));
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(hits);
        assertFalse(check.fatal());
        assertEquals(3, check.validHits().size());
        long titleMissing = check.violations().stream().filter(v -> "TITLE_MISSING".equals(v.get("code"))).count();
        long snapshotMissing = check.violations().stream().filter(v -> "SNAPSHOT_MISSING".equals(v.get("code"))).count();
        long anchorInvalid = check.violations().stream().filter(v -> "ANCHOR_TIME_INVALID".equals(v.get("code"))).count();
        assertEquals(2, titleMissing);
        assertEquals(2, snapshotMissing);
        assertEquals(1, anchorInvalid);
    }

    @Test
    void anchorTimeAcceptsBothContractFormats() {
        assertTrue(HitsContractValidator.validAnchorTime("2026-10-22 09:30:15"));
        assertTrue(HitsContractValidator.validAnchorTime("2026-10-22T09:30:15"));
        assertTrue(HitsContractValidator.validAnchorTime(null));
        assertTrue(HitsContractValidator.validAnchorTime(""));
        assertFalse(HitsContractValidator.validAnchorTime("2026/10/22"));
        assertFalse(HitsContractValidator.validAnchorTime("昨天"));
    }

    @Test
    void runtimeEmptyArrayPasses() {
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(List.of());
        assertFalse(check.fatal());
        assertTrue(check.validHits().isEmpty());
        assertTrue(check.violations().isEmpty());
    }

    @Test
    void runtimeConvertsToTypedHitAndKeepsExtras() {
        List<Object> hits = List.of(Map.of(
                "bizId", "1", "bizType", "X", "levelOverride", "P9", "customField", "v"));
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(hits);
        assertFalse(check.fatal());
        Hit hit = check.validHits().get(0);
        assertEquals("1", hit.getBizId());
        assertEquals("X", hit.getBizType());
        assertEquals("P9", hit.getExtras().get("levelOverride"));
        assertEquals("v", hit.getExtras().get("customField"));
        assertNull(hit.getTitle());
    }
}
