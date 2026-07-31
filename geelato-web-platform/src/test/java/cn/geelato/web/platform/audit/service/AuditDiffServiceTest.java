package cn.geelato.web.platform.audit.service;

import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditDiffService} 字段级 diff 逻辑测试。
 *
 * <p>用 namer/maskService 的真实实例（properties 默认配置），通过覆盖 namer 方法隔离 MetaManager 单例依赖。
 * 重点验证：系统字段跳过、新增/删除/更新明细、敏感字段脱敏。
 */
class AuditDiffServiceTest {

    private AuditDiffService diffService;
    private AuditMaskService maskService;

    @BeforeEach
    void setUp() {
        AuditLogProperties properties = new AuditLogProperties();
        maskService = new AuditMaskService(properties);
        // namer 用匿名子类覆盖元数据相关方法，避免依赖 MetaManager 单例
        AuditBusinessNamer namer = new AuditBusinessNamer(properties, null) {
            @Override
            public String fieldTitle(String entityName, String fieldName) {
                return fieldName; // 测试用：标题=字段名
            }

            @Override
            public String displayValue(String entityName, String fieldName, Object code) {
                return code == null ? null : code.toString(); // 测试用：展示值=原值
            }

            @Override
            public boolean isEncrypted(String entityName, String fieldName) {
                return false;
            }

            @Override
            public String bizNameValue(Map<String, Object> record, String bizNameColumn) {
                return null;
            }

            @Override
            public String entityTitle(String entityName) {
                return entityName;
            }
        };
        diffService = new AuditDiffService(namer, maskService, properties);
    }

    @Test
    void diff_onlyReturnsChangedFields() {
        Map<String, Object> before = map("status", "pending", "amount", 100, "name", "订单A", "id", "pk1");
        Map<String, Object> after = map("status", "confirmed", "amount", 100, "name", "订单B", "id", "pk1");

        List<AuditFieldChange> changes = diffService.diff("fp_order", before, after);

        assertEquals(2, changes.size());
        assertTrue(containsField(changes, "status"));
        assertTrue(containsField(changes, "name"));
        assertFalse(containsField(changes, "amount")); // 未变化
        assertFalse(containsField(changes, "id"));     // 系统字段
    }

    @Test
    void diff_detectsNullToValue() {
        Map<String, Object> before = map("status", null);
        Map<String, Object> after = map("status", "confirmed");

        List<AuditFieldChange> changes = diffService.diff("fp_order", before, after);
        assertEquals(1, changes.size());
        assertNull(changes.get(0).getOldValue());
        assertEquals("confirmed", changes.get(0).getNewValue());
    }

    @Test
    void diff_sensitiveField_isMasked() {
        Map<String, Object> before = map("mobilePhone", "13812345678");
        Map<String, Object> after = map("mobilePhone", "13987654321");

        List<AuditFieldChange> changes = diffService.diff("user", before, after);
        assertEquals(1, changes.size());
        AuditFieldChange c = changes.get(0);
        assertTrue(c.isSensitive());
        assertEquals("138****5678", c.getOldValue());
        assertEquals("139****4321", c.getNewValue());
    }

    @Test
    void insertDetail_recordsAllNewFields() {
        Map<String, Object> after = map("orderNo", "WBL-001", "amount", 200, "id", "pk1", "status", null);

        List<AuditFieldChange> changes = diffService.insertDetail("fp_order", after);
        // status=null 被跳过，id 系统字段被跳过
        assertEquals(2, changes.size());
        assertTrue(containsField(changes, "orderNo"));
        assertTrue(containsField(changes, "amount"));
        for (AuditFieldChange c : changes) {
            assertNull(c.getOldValue());
        }
    }

    @Test
    void deleteDetail_recordsSnapshotBeforeDelete() {
        Map<String, Object> before = map("orderNo", "WBL-001", "amount", 200, "id", "pk1");

        List<AuditFieldChange> changes = diffService.deleteDetail("fp_order", before);
        assertEquals(2, changes.size());
        for (AuditFieldChange c : changes) {
            assertNull(c.getNewValue());
            assertNotNull(c.getOldValue());
        }
    }

    @Test
    void diff_emptyAfter_returnsEmpty() {
        assertTrue(diffService.diff("fp_order", map(), null).isEmpty());
        assertTrue(diffService.diff("fp_order", null, map()).isEmpty());
    }

    @Test
    void diff_respectsMaxFieldsLimit() {
        AuditLogProperties props = new AuditLogProperties();
        props.setDetailMaxFields(3);
        AuditBusinessNamer namer = simpleNamer(props);
        AuditDiffService limited = new AuditDiffService(namer, new AuditMaskService(props), props);

        Map<String, Object> before = map();
        Map<String, Object> after = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            before.put("f" + i, "old" + i);
            after.put("f" + i, "new" + i);
        }
        List<AuditFieldChange> changes = limited.diff("e", before, after);
        assertEquals(3, changes.size());
    }

    private AuditBusinessNamer simpleNamer(AuditLogProperties props) {
        return new AuditBusinessNamer(props, null) {
            @Override
            public String fieldTitle(String e, String f) {
                return f;
            }

            @Override
            public String displayValue(String e, String f, Object c) {
                return c == null ? null : c.toString();
            }

            @Override
            public boolean isEncrypted(String e, String f) {
                return false;
            }
        };
    }

    private boolean containsField(List<AuditFieldChange> changes, String field) {
        return changes.stream().anyMatch(c -> field.equals(c.getField()));
    }

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
