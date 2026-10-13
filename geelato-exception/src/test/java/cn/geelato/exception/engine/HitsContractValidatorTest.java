package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.HitsContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                Map.of("bizType", "ORDER", "bizId", "1"),
                Map.of("bizType", "ORDER"),
                Map.of("bizId", "3"),
                "garbage",
                Map.of("bizType", "ORDER", "bizId", "5", "levelOverride", "P9"),
                Map.of("bizType", "ORDER", "bizId", "6", "levelOverride", "P1"));
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(hits);
        assertFalse(check.fatal());
        assertEquals(2, check.validHits().size());
        assertEquals(4, check.violations().size());
    }

    @Test
    void runtimeEmptyArrayPasses() {
        HitsContractValidator.RuntimeCheck check = HitsContractValidator.validateRuntime(List.of());
        assertFalse(check.fatal());
        assertTrue(check.validHits().isEmpty());
        assertTrue(check.violations().isEmpty());
    }
}
