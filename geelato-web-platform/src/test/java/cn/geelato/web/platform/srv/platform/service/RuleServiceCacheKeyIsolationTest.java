package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 缓存 key 的数据权限隔离不变量:数据权限规则注入 originalWhere、租户过滤注入 where,
 * 两者均纳入签名——不同权限不同 key,相同有效规则共享同一 key。
 */
class RuleServiceCacheKeyIsolationTest {

    private final RuleService ruleService = new RuleService();

    @Test
    void differentDataPermissionProducesDifferentKeys() {
        QueryCommand userA = command("creator='uA'");
        QueryCommand userB = command("creator='uB'");
        QueryCommand deptRule = command("dept_id in ('D3')");

        assertNotEquals(ruleService.buildCacheKey(userA, "list"), ruleService.buildCacheKey(userB, "list"));
        assertNotEquals(ruleService.buildCacheKey(userA, "list"), ruleService.buildCacheKey(deptRule, "list"));
    }

    @Test
    void sameDataPermissionSharesKey() {
        assertEquals(ruleService.buildCacheKey(command("dept_id in ('D3')"), "list"),
                ruleService.buildCacheKey(command("dept_id in ('D3')"), "list"));
    }

    @Test
    void tenantFilterInWhereAlsoIsolates() {
        QueryCommand tenantA = command("creator='uA'", "geelato");
        QueryCommand tenantB = command("creator='uA'", "other");

        assertNotEquals(ruleService.buildCacheKey(tenantA, "list"), ruleService.buildCacheKey(tenantB, "list"));
    }

    @Test
    void suffixDistinguishesShapes() {
        QueryCommand command = command("creator='uA'");
        assertNotEquals(ruleService.buildCacheKey(command, "list"), ruleService.buildCacheKey(command, "total"));
    }

    private static QueryCommand command(String originalWhere) {
        return command(originalWhere, "geelato");
    }

    private static QueryCommand command(String originalWhere, String tenantCode) {
        QueryCommand command = new QueryCommand();
        command.setEntityName("platform_form");
        command.setOriginalWhere(originalWhere);
        command.setWhere(new FilterGroup().addFilter("tenantCode", FilterGroup.Operator.eq, tenantCode));
        return command;
    }
}
