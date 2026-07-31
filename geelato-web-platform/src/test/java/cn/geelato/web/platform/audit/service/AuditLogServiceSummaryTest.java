package cn.geelato.web.platform.audit.service;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.enums.AuditCaptureLayer;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.model.AuditLogQuery;
import cn.geelato.web.platform.audit.store.AuditLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditLogService#buildSummary} 摘要构建测试。
 *
 * <p>用不落库的 store stub，只验证中文摘要拼接逻辑。
 */
class AuditLogServiceSummaryTest {

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        AuditLogProperties properties = new AuditLogProperties();
        service = new AuditLogService(new NoopStore(), properties);
    }

    @Test
    void buildSummary_annotatedLayer_withChanges() {
        AuditLogRecord a = baseRecord();
        a.setOperName("审批");
        a.setCaptureLayer(AuditCaptureLayer.ANNOTATED.name());
        AuditFieldChange c1 = new AuditFieldChange("status", "status", "状态",
                "pending", "confirmed", "待审批", "已通过", false);
        AuditFieldChange c2 = new AuditFieldChange("amount", "amount", "金额",
                100, 200, 100, 200, false);

        String summary = service.buildSummary(a, List.of(c1, c2));

        // 期望：张三 审批 运单 WBL-2024-001，状态:待审批→已通过，金额:100→200
        assertTrue(summary.contains("张三"), summary);
        assertTrue(summary.contains("审批"), summary);
        assertTrue(summary.contains("运单"), summary);
        assertTrue(summary.contains("WBL-2024-001"), summary);
        assertTrue(summary.contains("待审批→已通过"), summary);
        assertTrue(summary.contains("金额:100→200"), summary);
    }

    @Test
    void buildSummary_ormFallback_appendsLe() {
        AuditLogRecord a = baseRecord();
        a.setOperName("修改");
        a.setCaptureLayer(AuditCaptureLayer.ORM_FALLBACK.name());

        String summary = service.buildSummary(a, null);

        // 兜底层自动补"了"：张三 修改了 运单 WBL-2024-001
        assertTrue(summary.contains("修改了"), summary);
    }

    @Test
    void buildSummary_withDelegator() {
        AuditLogRecord a = baseRecord();
        a.setOperName("审批");
        a.setCaptureLayer(AuditCaptureLayer.ANNOTATED.name());
        a.setDelegatorId("u-002");
        a.setDelegatorName("李四");

        String summary = service.buildSummary(a, null);

        // 期望包含：(代李四)
        assertTrue(summary.contains("(代李四)"), summary);
    }

    @Test
    void buildSummary_truncatesWhenTooManyChanges() {
        AuditLogRecord a = baseRecord();
        a.setOperName("修改");
        a.setCaptureLayer(AuditCaptureLayer.ORM_FALLBACK.name());
        // 默认 summaryMaxFields=5，给 8 个
        AuditFieldChange[] changes = new AuditFieldChange[8];
        for (int i = 0; i < 8; i++) {
            changes[i] = new AuditFieldChange("f" + i, "f" + i, "字段" + i,
                    "old" + i, "new" + i, "old" + i, "new" + i, false);
        }

        String summary = service.buildSummary(a, List.of(changes));
        assertTrue(summary.contains("等共8项"), summary);
    }

    @Test
    void toJson_serializesChanges() {
        AuditFieldChange c = new AuditFieldChange("status", "status", "状态",
                "pending", "confirmed", "待审批", "已通过", false);
        String json = service.toJson(List.of(c));
        assertNotNull(json);
        assertTrue(json.contains("\"field\":\"status\""));
        assertTrue(json.contains("\"newDisplay\":\"已通过\""));
    }

    @Test
    void toJson_emptyList_returnsNull() {
        assertNull(service.toJson(null));
        assertNull(service.toJson(List.of()));
    }

    private AuditLogRecord baseRecord() {
        AuditLogRecord a = new AuditLogRecord();
        a.setActorName("张三");
        a.setActorId("u-001");
        a.setEntityTitle("运单");
        a.setTargetName("WBL-2024-001");
        a.setTargetId("pk-001");
        a.setBizType("freight_order");
        return a;
    }

    /** 不落库的 store stub，仅用于隔离测试。 */
    static class NoopStore implements AuditLogStore {
        @Override
        public void store(AuditLogRecord auditLog) {
            // no-op
        }

        @Override
        public ApiPagedResult<AuditLogRecord> page(AuditLogQuery query) {
            return new ApiPagedResult<>();
        }
    }
}
