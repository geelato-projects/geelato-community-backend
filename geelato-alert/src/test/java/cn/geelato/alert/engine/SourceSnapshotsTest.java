package cn.geelato.alert.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSnapshotsTest {

    private Map<String, Object> row(String id, String detailJson) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("rule_code", "EXC-001");
        row.put("rule_name", "收款超时");
        row.put("domain_code", "FIN");
        row.put("level", "P0");
        row.put("status", "OPEN");
        row.put("biz_label", "HQL" + id);
        row.put("title", "未收款");
        row.put("anchor_elapsed", 120);
        row.put("detail_json", detailJson);
        return row;
    }

    @Test
    void singleRoundTripKeepsSnapshotDetail() {
        String json = SourceSnapshots.writeSingle(row("9001",
                "{\"title\":\"未收款\",\"snapshot\":{\"amountLocal\":500.5,\"currency\":\"CNY\",\"customerName\":\"客户A\",\"orderNo\":\"SO-1\"}}"));
        Map<String, Object> source = SourceSnapshots.parseItem(json);
        assertEquals("9001", source.get("id"));
        assertEquals("EXC-001", source.get("ruleCode"));
        assertEquals("HQL9001", source.get("bizLabel"));
        assertEquals("未收款", source.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) source.get("detail");
        assertEquals("未收款", detail.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) detail.get("snapshot");
        assertEquals(500.5, snapshot.get("amountLocal"));
        assertEquals("客户A", snapshot.get("customerName"));
        assertEquals("SO-1", snapshot.get("orderNo"));
    }

    @Test
    void groupItemExtractsAmountBriefFromSnapshot() {
        String json = SourceSnapshots.writeGroupAll(List.of(row("1",
                "{\"snapshot\":{\"amountLocal\":100,\"currency\":\"USD\"}}")));
        List<Map<String, Object>> items = SourceSnapshots.parseItems(json);
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals("EXC-001", item.get("ruleCode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> brief = (Map<String, Object>) item.get("detail");
        assertEquals(100, brief.get("amountLocal"));
        assertEquals("USD", brief.get("currency"));
        assertEquals(100.0, SourceSnapshots.amountOf(item));
    }

    @Test
    void mergeGroupDedupesByIdAndAppends() {
        String json = SourceSnapshots.writeGroupAll(List.of(row("1", null)));
        json = SourceSnapshots.mergeGroup(json, row("2", null));
        json = SourceSnapshots.mergeGroup(json, row("1", null));
        List<Map<String, Object>> items = SourceSnapshots.parseItems(json);
        assertEquals(2, items.size());
        assertEquals("1", items.get(0).get("id"));
        assertEquals("2", items.get(1).get("id"));
    }

    @Test
    void parseItemTakesFirstOfArrayAndNullOnBlank() {
        assertNull(SourceSnapshots.parseItem(null));
        assertNull(SourceSnapshots.parseItem(""));
        String json = SourceSnapshots.writeGroupAll(List.of(row("1", null), row("2", null)));
        assertEquals("1", SourceSnapshots.parseItem(json).get("id"));
    }

    @Test
    void malformedStructureHardFails() {
        assertThrows(IllegalStateException.class, () -> SourceSnapshots.parseItem("\"scalar\""));
        assertThrows(IllegalStateException.class, () -> SourceSnapshots.parseItems("{\"id\":\"1\"}"));
        assertThrows(IllegalStateException.class, () -> SourceSnapshots.parseItems("not-json"));
    }

    @Test
    void amountOfToleratesMissingDetail() {
        assertTrue(SourceSnapshots.amountOf(Map.of("detail", Map.of())) == null);
        assertNull(SourceSnapshots.amountOf(Map.of()));
    }
}
