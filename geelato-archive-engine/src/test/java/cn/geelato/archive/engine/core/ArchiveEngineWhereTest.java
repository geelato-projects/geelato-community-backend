package cn.geelato.archive.engine.core;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.enums.PolicyTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选批条件构造（buildSourceWhere）的纯逻辑测试。
 * 语义：DEFAULT → create_at < watermark（参数化）；CUSTOM → 条件原样包裹；
 * include_deleted=false 且源表有 del_status 列时追加软删过滤。
 * 条件仅参与"选 id"，搬移与删除始终按 id IN。
 */
class ArchiveEngineWhereTest {

    @Test
    void defaultPolicyUsesParameterizedCreateAtWindow() {
        ArchivePolicy policy = new ArchivePolicy();
        policy.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        Date watermark = new Date(1700000000000L);

        ArchiveEngine.WhereClause where = ArchiveEngine.buildSourceWhere(policy, watermark, true);

        assertEquals("(create_at < ?)", where.sql());
        assertEquals(1, where.params().size());
        assertEquals(watermark, where.params().get(0));
    }

    @Test
    void customPolicyWrapsConditionAsIs() {
        ArchivePolicy policy = new ArchivePolicy();
        policy.setPolicyType(PolicyTypeEnum.CUSTOM.name());
        policy.setWhereCondition("batch < 'B2024-001'");

        ArchiveEngine.WhereClause where = ArchiveEngine.buildSourceWhere(policy, null, true);

        assertEquals("((batch < 'B2024-001'))", where.sql());
        assertTrue(where.params().isEmpty());
    }

    @Test
    void excludeDeletedAppendsDelStatusFilter() {
        ArchivePolicy policy = new ArchivePolicy();
        policy.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        policy.setIncludeDeleted(Boolean.FALSE);

        ArchiveEngine.WhereClause where = ArchiveEngine.buildSourceWhere(policy, new Date(), true);

        assertTrue(where.sql().contains("(del_status = 0 OR del_status IS NULL)"));
    }

    @Test
    void includeDeletedByDefaultDoesNotAppendFilter() {
        ArchivePolicy policy = new ArchivePolicy();
        policy.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        // includeDeleted 默认 true

        ArchiveEngine.WhereClause where = ArchiveEngine.buildSourceWhere(policy, new Date(), true);

        assertFalse(where.sql().contains("del_status"));
    }

    @Test
    void tableWithoutDelStatusColumnNeverAppendsFilter() {
        ArchivePolicy policy = new ArchivePolicy();
        policy.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        policy.setIncludeDeleted(Boolean.FALSE);

        ArchiveEngine.WhereClause where = ArchiveEngine.buildSourceWhere(policy, new Date(), false);

        assertFalse(where.sql().contains("del_status"));
    }
}
