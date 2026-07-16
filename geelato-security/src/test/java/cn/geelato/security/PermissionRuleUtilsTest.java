package cn.geelato.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRuleUtilsTest {

    @Test
    void shouldReplaceCurrentUserVariable() {
        Permission permission = permission("demo", "creator=#currentUser.userId#");
        User user = user("u1");

        String rule = PermissionRuleUtils.replaceRuleVariable(permission, user);

        assertEquals("creator='u1'", rule);
    }

    @Test
    void shouldReplaceDefaultOrgVariable() {
        Permission permission = permission("demo", "dept_id=#currentUser.defaultOrg.deptId#");
        User user = user("u1");
        UserOrg defaultOrg = new UserOrg();
        defaultOrg.setDeptId("dept-1");
        user.setDefaultOrg(defaultOrg);

        String rule = PermissionRuleUtils.replaceRuleVariable(permission, user);

        assertEquals("dept_id='dept-1'", rule);
    }

    @Test
    void shouldFailWhenVariableCannotBeResolved() {
        Permission permission = permission("demo", "creator=#currentUser.unknownField#");
        User user = user("u1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PermissionRuleUtils.replaceRuleVariable(permission, user));

        assertTrue(ex.getMessage().contains("#currentUser.unknownField#"));
        assertTrue(ex.getMessage().contains("entity=demo"));
    }

    private static Permission permission(String entity, String rule) {
        Permission permission = new Permission();
        permission.setEntity(entity);
        permission.setRule(rule);
        permission.setWeight(1);
        return permission;
    }

    private static User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }
}
