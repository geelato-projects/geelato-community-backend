package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.KeyCaseNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyCaseNormalizerTest {

    @Test
    void convertsSnakeKeysRecursively() {
        Object out = KeyCaseNormalizer.normalize(Map.of(
                "biz_id", "1",
                "owner_org_id", "ORG7",
                "camelAlready", List.of(Map.of("inner_key", "v")),
                "plain", 3));
        Map<?, ?> map = assertInstanceOf(Map.class, out);
        assertEquals("1", map.get("bizId"));
        assertEquals("ORG7", map.get("ownerOrgId"));
        assertEquals("v", ((Map<?, ?>) ((List<?>) map.get("camelAlready")).get(0)).get("innerKey"));
        assertEquals(3, map.get("plain"));
        assertFalse(map.containsKey("biz_id"));
    }

    @Test
    void scalarsAndEmptyContainersPassThrough() {
        assertEquals("x", KeyCaseNormalizer.normalize("x"));
        assertEquals(7, KeyCaseNormalizer.normalize(7));
        assertNull(KeyCaseNormalizer.normalize(null));
        assertTrue(((List<?>) KeyCaseNormalizer.normalize(List.of())).isEmpty());
    }

    @Test
    void keysWithoutUnderscoreUntouched() {
        Map<?, ?> out = (Map<?, ?>) KeyCaseNormalizer.normalize(Map.of("ID", 1, "aB", 2));
        assertEquals(1, out.get("ID"));
        assertEquals(2, out.get("aB"));
    }
}
