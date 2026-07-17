package cn.geelato.web.platform.boot.filter;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.security.Permission;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformFluentQueryFilterInjectorTest {

    private final PlatformFluentQueryFilterInjector injector = new PlatformFluentQueryFilterInjector();

    @AfterEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    @Test
    void shouldInjectTenantAndDataPermissionForFluentQuery() {
        User user = new User();
        user.setUserId("U1001");
        Permission permission = new Permission();
        permission.setEntity("demo_entity");
        permission.setRule("dept_id = #currentUser.deptId#");
        permission.setWeight(10);
        user.setDeptId("D01");
        user.setDataPermissions(java.util.List.of(permission));
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));

        QueryCommand command = new QueryCommand();
        command.setEntityName("demo_entity");

        injector.inject(command, null);

        assertNotNull(command.getWhere());
        assertEquals("tenantCode", command.getWhere().getFilters().get(0).getField());
        assertEquals("geelato", command.getWhere().getFilters().get(0).getValue());
        assertEquals("dept_id = 'D01'", command.getOriginalWhere());
    }

    @Test
    void shouldFallbackToCreatorRuleWhenNoDataPermissionExistsForFluentQuery() {
        User user = new User();
        user.setUserId("U1002");
        SecurityContext.setCurrentUser(user);

        QueryCommand command = new QueryCommand();
        command.setEntityName("demo_entity");

        injector.inject(command, null);

        assertEquals("creator='U1002'", command.getOriginalWhere());
    }
}
