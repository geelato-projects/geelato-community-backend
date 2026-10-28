package cn.geelato.exception.engine.support;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void withEngineContextForcesSystemPrincipalAndRuleTenant() {
        User caller = new User();
        caller.setUserId("u3");
        SecurityContext.setCurrentUser(caller);
        SecurityContext.setCurrentTenant(new Tenant("t9"));

        Integer seen = EngineContextExecutor.withEngineContext("t1", () -> {
            assertTrue(SecurityContext.isSystemPrincipal());
            assertEquals(EngineContextExecutor.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
            assertEquals("t1", SecurityContext.getCurrentTenant().getCode());
            return 7;
        });

        assertEquals(7, seen);
        assertSame(caller, SecurityContext.getCurrentUser());
        assertEquals("t9", SecurityContext.getCurrentTenant().getCode());
    }

    @Test
    void withParticipantPrincipalSwapsIdentityWithEngineAsDelegate() {
        EngineContextExecutor.withContext("t1", () -> {
            User seen = EngineContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> {
                User current = SecurityContext.getCurrentUser();
                assertEquals("p1", current.getUserId());
                assertEquals("参与者甲", current.getUserName());
                assertEquals(EngineContextExecutor.SYSTEM_USER_ID, current.getDelegateUserId());
                assertEquals(EngineContextExecutor.SYSTEM_USER_NAME, current.getDelegateUserName());
                assertFalse(SecurityContext.isSystemPrincipal());
                return current;
            });
            assertNotNull(seen);
            assertTrue(SecurityContext.isSystemPrincipal());
            return null;
        });
    }

    @Test
    void withParticipantPrincipalRestoresOnTaskFailure() {
        EngineContextExecutor.withContext("t1", () -> {
            assertThrows(RuntimeException.class, () ->
                    EngineContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> {
                        throw new RuntimeException("boom");
                    }));
            assertEquals(EngineContextExecutor.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
            return null;
        });
    }

    @Test
    void withParticipantPrincipalRejectsNonSystemContext() {
        User caller = new User();
        caller.setUserId("u4");
        SecurityContext.setCurrentUser(caller);
        assertThrows(IllegalStateException.class,
                () -> EngineContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> null));
    }

    @Test
    void withParticipantPrincipalRejectsBlankUserId() {
        EngineContextExecutor.withContext("t1", () -> {
            assertThrows(IllegalStateException.class,
                    () -> EngineContextExecutor.withParticipantPrincipal(" ", "参与者甲", () -> null));
            return null;
        });
    }
}
