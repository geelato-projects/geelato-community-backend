package cn.geelato.core.mql.command;

import cn.geelato.core.mql.filter.FilterGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link QueryCommand#signatureString()} 与 {@link FilterGroup#signatureString()} 单元测试。
 * <p>
 * 这是 core 层的纯函数测试，不涉及缓存概念、不依赖 Spring/SecurityContext/数据库。
 * 仅验证"查询的规范化签名"的完整性（不同查询必产生不同签名）与稳定性（相同查询必产生相同签名）。
 * </p>
 * <p>
 * 安全意义：签名是上层缓存 key 的基础，若不同租户/不同 where 产生相同签名，
 * 将导致跨租户数据泄露。这些用例是该隔离能力的回归保护。
 * </p>
 */
@DisplayName("查询签名 signatureString()")
class QueryCommandSignatureTest {

    // ==================== 基础稳定性 ====================

    @Test
    @DisplayName("无状态：同一对象多次调用结果一致")
    void signatureIsStatelessAndStable() {
        QueryCommand c = sampleCommand();
        assertEquals(c.signatureString(), c.signatureString());
    }

    @Test
    @DisplayName("等价重建的两个命令签名相同")
    void equivalentCommandsProduceSameSignature() {
        assertEquals(sampleCommand().signatureString(), sampleCommand().signatureString());
    }

    @Test
    @DisplayName("空命令签名非 null")
    void emptyCommandSignatureNotNull() {
        assertNotNull(new QueryCommand().signatureString());
    }

    // ==================== where 维度（隔离关键） ====================

    @Test
    @DisplayName("不同 where 值签名应不同")
    void differentWhereValuesProduceDifferentSignatures() {
        QueryCommand c1 = commandWithWhere(filter("name", "eq", "A"));
        QueryCommand c2 = commandWithWhere(filter("name", "eq", "B"));
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("不同 where 字段签名应不同")
    void differentWhereFieldsProduceDifferentSignatures() {
        QueryCommand c1 = commandWithWhere(filter("name", "eq", "A"));
        QueryCommand c2 = commandWithWhere(filter("code", "eq", "A"));
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("不同操作符签名应不同（同字段同值）")
    void differentOperatorsProduceDifferentSignatures() {
        QueryCommand c1 = commandWithWhere(filter("age", "gte", "18"));
        QueryCommand c2 = commandWithWhere(filter("age", "gt", "18"));
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("租户隔离：不同 tenantCode 注入到 where 签名应不同")
        void differentTenantInWhereProducesDifferentSignatures() {
        // 模拟 SPI 注入：两个命令业务过滤完全相同，仅 tenantCode 不同
        QueryCommand c1 = commandWithWhere(filter("name", "eq", "A"));
        c1.getWhere().addFilter("tenantCode", "TENANT_A");
        QueryCommand c2 = commandWithWhere(filter("name", "eq", "A"));
        c2.getWhere().addFilter("tenantCode", "TENANT_B");
        assertNotEquals(c1.signatureString(), c2.signatureString(),
                "不同租户的相同业务查询必须产生不同签名，否则缓存会导致跨租户数据泄露");
    }

    // ==================== originalWhere 维度（数据权限） ====================

    @Test
    @DisplayName("不同 originalWhere（数据权限）签名应不同")
    void differentOriginalWhereProducesDifferentSignatures() {
        QueryCommand c1 = commandWithWhere(filter("name", "eq", "A"));
        c1.setOriginalWhere("creator='u1'");
        QueryCommand c2 = commandWithWhere(filter("name", "eq", "A"));
        c2.setOriginalWhere("creator='u2'");
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    // ==================== 其他维度 ====================

    @Test
    @DisplayName("不同 fields（@fs）签名应不同")
    void differentFieldsProduceDifferentSignatures() {
        QueryCommand c1 = commandWithFields("id", "name");
        QueryCommand c2 = commandWithFields("id", "name", "status");
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("fields 顺序不同签名应相同（排序稳定性）")
    void fieldsOrderDoesNotAffectSignature() {
        QueryCommand c1 = commandWithFields("name", "id");
        QueryCommand c2 = commandWithFields("id", "name");
        assertEquals(c1.signatureString(), c2.signatureString(),
                "fields 数组排序后进签名，顺序无关");
    }

    @Test
    @DisplayName("不同 viewTemplateParams（@pf）签名应不同")
    void differentViewTemplateParamsProduceDifferentSignatures() {
        QueryCommand c1 = commandWithPf(Map.of("orgId", "1"));
        QueryCommand c2 = commandWithPf(Map.of("orgId", "2"));
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("viewTemplateParams 条目顺序不同签名应相同（TreeMap 稳定性）")
    void viewTemplateParamsOrderDoesNotAffectSignature() {
        Map<String, Object> pf1 = new HashMap<>();
        pf1.put("a", "1");
        pf1.put("b", "2");
        Map<String, Object> pf2 = new HashMap<>();
        pf2.put("b", "2");
        pf2.put("a", "1");
        assertEquals(commandWithPf(pf1).signatureString(), commandWithPf(pf2).signatureString());
    }

    @Test
    @DisplayName("不同分页参数签名应不同")
    void differentPagingProducesDifferentSignatures() {
        QueryCommand c1 = sampleCommand();
        c1.setPageNum(1);
        c1.setPageSize(20);
        QueryCommand c2 = sampleCommand();
        c2.setPageNum(2);
        c2.setPageSize(20);
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    @Test
    @DisplayName("不同 orderBy 签名应不同")
    void differentOrderByProducesDifferentSignatures() {
        QueryCommand c1 = sampleCommand();
        c1.setOrderBy("name asc");
        QueryCommand c2 = sampleCommand();
        c2.setOrderBy("name desc");
        assertNotEquals(c1.signatureString(), c2.signatureString());
    }

    // ==================== FilterGroup.signatureString 直测 ====================

    @Nested
    @DisplayName("FilterGroup 签名")
    class FilterGroupSignature {

        @Test
        @DisplayName("空 FilterGroup 签名非 null")
        void emptyGroupNotNull() {
            assertNotNull(new FilterGroup().signatureString());
        }

        @Test
        @DisplayName("单个 filter 签名含 field/operator/value")
        void singleFilterSignature() {
            FilterGroup fg = new FilterGroup();
            fg.addFilter("name", "A");
            String sig = fg.signatureString();
            assert sig.contains("name");
            assert sig.contains("eq");
            assert sig.contains("A");
        }

        @Test
        @DisplayName("嵌套 childFilterGroup 参与签名")
        void childGroupIncluded() {
            FilterGroup parent = new FilterGroup();
            parent.addFilter("a", "1");
            FilterGroup child = new FilterGroup(FilterGroup.Logic.or);
            child.addFilter("b", "2");
            parent.getChildFilterGroup().add(child);

            FilterGroup parentNoChild = new FilterGroup();
            parentNoChild.addFilter("a", "1");

            assertNotEquals(parent.signatureString(), parentNoChild.signatureString(),
                    "有无 childFilterGroup 必须产生不同签名");
        }
    }

    // ==================== 辅助构建方法 ====================

    private QueryCommand sampleCommand() {
        QueryCommand c = new QueryCommand();
        c.setEntityName("platform_dev_table");
        FilterGroup fg = new FilterGroup();
        fg.addFilter("name", "eq", "sample");
        c.setWhere(fg);
        c.setFields(new String[]{"id", "name"});
        c.setOrderBy("id asc");
        c.setPageNum(1);
        c.setPageSize(20);
        return c;
    }

    private QueryCommand commandWithWhere(FilterGroup.Filter f) {
        QueryCommand c = new QueryCommand();
        c.setEntityName("platform_dev_table");
        FilterGroup fg = new FilterGroup();
        fg.getFilters().add(f);
        c.setWhere(fg);
        return c;
    }

    private QueryCommand commandWithFields(String... fields) {
        QueryCommand c = new QueryCommand();
        c.setEntityName("platform_dev_table");
        c.setWhere(new FilterGroup());
        c.setFields(fields);
        return c;
    }

    private QueryCommand commandWithPf(Map<String, Object> pf) {
        QueryCommand c = new QueryCommand();
        c.setEntityName("platform_dev_table");
        c.setWhere(new FilterGroup());
        c.setViewTemplateParams(pf);
        return c;
    }

    private FilterGroup.Filter filter(String field, String op, String value) {
        FilterGroup.Operator operator = FilterGroup.Operator.fromString(op);
        return new FilterGroup.Filter(field, operator, value);
    }
}
