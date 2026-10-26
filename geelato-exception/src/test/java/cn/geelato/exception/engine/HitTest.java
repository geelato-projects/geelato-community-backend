package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromMapMinimalHitHasOnlyBizId() {
        Hit hit = Hit.fromMap(Map.of("bizId", "1"));
        assertEquals("1", hit.getBizId());
        assertNull(hit.getBizLabel());
        assertNull(hit.getTitle());
        assertTrue(hit.getExtras().isEmpty());
    }

    @Test
    void fromMapExtractsContractFieldsAndCollectsExtras() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bizType", "ORDER");
        map.put("bizId", 42L);
        map.put("bizLabel", "ECO-1");
        map.put("title", "订单 ECO-1 金额 500 ETD 超期 3 天");
        map.put("anchorTime", "2026-10-14 10:00:00");
        map.put("snapshot", Map.of("amount", 500));
        map.put("ownerId", "u1");
        map.put("ownerOrgId", "ORG1");
        map.put("anything", "extra");
        Hit hit = Hit.fromMap(map);
        assertEquals("ORDER", hit.getBizType());
        assertEquals("42", hit.getBizId());
        assertEquals("ECO-1", hit.getBizLabel());
        assertEquals("订单 ECO-1 金额 500 ETD 超期 3 天", hit.getTitle());
        assertEquals("2026-10-14 10:00:00", hit.getAnchorTime());
        assertEquals(Map.of("amount", 500), hit.getSnapshot());
        // owner 系不再属契约，自动降级 extras（BIZ_FIELD 显式声明仍可按名取）
        assertEquals("u1", hit.getExtras().get("ownerId"));
        assertEquals("ORG1", hit.getExtras().get("ownerOrgId"));
        assertEquals("extra", hit.getExtras().get("anything"));
        assertEquals(3, hit.getExtras().size());
    }

    @Test
    void legacyBusinessFieldsFallToExtrasHarmlessly() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bizId", "1");
        map.put("customerName", "海桥广州");
        map.put("ownerName", "Lan Hu");
        map.put("hitBrief", "旧别名");
        map.put("bizNo", "OBSZ001");
        Hit hit = Hit.fromMap(map);
        assertNull(hit.getTitle());
        assertNull(hit.getBizLabel());
        assertEquals("海桥广州", hit.getExtras().get("customerName"));
        assertEquals("Lan Hu", hit.getExtras().get("ownerName"));
        assertEquals("旧别名", hit.getExtras().get("hitBrief"));
        assertEquals("OBSZ001", hit.getExtras().get("bizNo"));
    }

    @Test
    void serializationKeepsFullContractShapeAndFlattensExtras() throws Exception {
        Hit hit = Hit.fromMap(Map.of("bizId", "1", "foo", "bar"));
        String json = MAPPER.writeValueAsString(hit);
        assertTrue(json.contains("\"bizId\":\"1\""));
        assertTrue(json.contains("\"foo\":\"bar\""));
        for (String field : new String[]{"bizType", "bizLabel", "title", "anchorTime", "snapshot"}) {
            assertTrue(json.contains("\"" + field + "\":null"), field + " 应显式为 null");
        }
        assertFalse(json.contains("extras"));
    }

    @Test
    void bizTypeScriptFirstWithRuleFallback() {
        // 脚本返回优先：defaultBizType 不覆盖非空脚本值
        Hit hit = Hit.fromMap(Map.of("bizId", "1", "bizType", "FROM_SCRIPT"));
        hit.defaultBizType("FROM_RULE");
        assertEquals("FROM_SCRIPT", hit.getBizType());
        // 脚本未返回：规则值兜底
        Hit empty = Hit.fromMap(Map.of("bizId", "1"));
        empty.defaultBizType("FROM_RULE");
        assertEquals("FROM_RULE", empty.getBizType());
        // 两者皆空：保持 null（PipelineEngine 记 violation）
        Hit none = Hit.fromMap(Map.of("bizId", "1"));
        none.defaultBizType(null);
        assertNull(none.getBizType());
    }

    @Test
    void getFieldCoversContractAndExtras() {
        Hit hit = Hit.fromMap(Map.of("bizId", "1", "salesmanIds", java.util.List.of("a", "b")));
        assertEquals("1", hit.getField("bizId"));
        assertNull(hit.getField("ownerId"));
        assertEquals(java.util.List.of("a", "b"), hit.getField("salesmanIds"));
        assertNull(hit.getField(null));
    }
}
