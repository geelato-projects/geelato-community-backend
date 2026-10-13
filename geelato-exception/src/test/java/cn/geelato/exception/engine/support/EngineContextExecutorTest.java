package cn.geelato.exception.engine.support;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineContextExecutorTest {

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    @Test
    void withContextInjectsSystemPrincipalWithTenant() {
        assertNull(SecurityContext.getCurrentUser());
        String seen = EngineContextExecutor.withContext("t1", () -> {
            User user = SecurityContext.getCurrentUser();
            assertNotNull(user);
            assertEquals(EngineContextExecutor.SYSTEM_USER_ID, user.getUserId());
            assertTrue(SecurityContext.isSystemPrincipal());
            assertEquals("t1", SecurityContext.getCurrentTenant().getCode());
            return "ok";
        });
        assertEquals("ok", seen);
        assertNull(SecurityContext.getCurrentUser());
        assertNull(SecurityContext.getCurrentTenant());
    }

    @Test
    void withContextKeepsExistingIdentity() {
        User existing = new User();
        existing.setUserId("u1");
        SecurityContext.setCurrentUser(existing);
        SecurityContext.setCurrentTenant(new Tenant("t9"));

        EngineContextExecutor.withContext("t1", () -> null);

        assertSame(existing, SecurityContext.getCurrentUser());
        assertEquals("t9", SecurityContext.getCurrentTenant().getCode());
    }

    @Test
    void withSystemContextRunsTenantlessSystemPrincipalAndRestores() {
        User caller = new User();
        caller.setUserId("u2");
        SecurityContext.setCurrentUser(caller);
        SecurityContext.setCurrentTenant(new Tenant("t9"));

        Integer seen = EngineContextExecutor.withSystemContext(() -> {
            assertTrue(SecurityContext.isSystemPrincipal());
            assertEquals(EngineContextExecutor.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
            assertNull(SecurityContext.getCurrentTenant());
            return 42;
        });

        assertEquals(42, seen);
        assertSame(caller, SecurityContext.getCurrentUser());
        assertEquals("t9", SecurityContext.getCurrentTenant().getCode());
    }

    @Test
    void withSystemContextOnBareThreadLeavesNoResidue() {
        Integer seen = EngineContextExecutor.withSystemContext(() -> {
            assertTrue(SecurityContext.isSystemPrincipal());
            assertNull(SecurityContext.getCurrentTenant());
            return 1;
        });

        assertEquals(1, seen);
        assertNull(SecurityContext.getCurrentUser());
        assertNull(SecurityContext.getCurrentTenant());
    }
}
