package cn.geelato.alert.engine.support;

import cn.geelato.alert.engine.EngineConsts;
import cn.geelato.security.SecurityContext;
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

class AlertContextExecutorTest {

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    @Test
    void withContextInjectsSystemPrincipalWithTenant() {
        assertNull(SecurityContext.getCurrentUser());
        String seen = AlertContextExecutor.withContext("t1", () -> {
            User user = SecurityContext.getCurrentUser();
            assertNotNull(user);
            assertEquals(EngineConsts.SYSTEM_USER_ID, user.getUserId());
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

        AlertContextExecutor.withContext("t1", () -> null);

        assertSame(existing, SecurityContext.getCurrentUser());
    }

    @Test
    void withParticipantPrincipalSwapsIdentityWithEngineAsDelegate() {
        AlertContextExecutor.withContext("t1", () -> {
            AlertContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> {
                User current = SecurityContext.getCurrentUser();
                assertEquals("p1", current.getUserId());
                assertEquals("参与者甲", current.getUserName());
                assertEquals(EngineConsts.SYSTEM_USER_ID, current.getDelegateUserId());
                assertEquals(EngineConsts.SYSTEM_USER_NAME, current.getDelegateUserName());
                assertFalse(SecurityContext.isSystemPrincipal());
                return null;
            });
            assertEquals(EngineConsts.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
            return null;
        });
    }

    @Test
    void withParticipantPrincipalBlankUserIdRunsAsEngineIdentity() {
        AlertContextExecutor.withContext("t1", () -> {
            AlertContextExecutor.withParticipantPrincipal(null, null, () -> {
                assertEquals(EngineConsts.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
                return null;
            });
            return null;
        });
    }

    @Test
    void withParticipantPrincipalRestoresOnTaskFailure() {
        AlertContextExecutor.withContext("t1", () -> {
            assertThrows(RuntimeException.class, () ->
                    AlertContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> {
                        throw new RuntimeException("boom");
                    }));
            assertEquals(EngineConsts.SYSTEM_USER_ID, SecurityContext.getCurrentUser().getUserId());
            return null;
        });
    }

    @Test
    void withParticipantPrincipalRejectsNonSystemContext() {
        User caller = new User();
        caller.setUserId("u2");
        SecurityContext.setCurrentUser(caller);
        assertThrows(IllegalStateException.class,
                () -> AlertContextExecutor.withParticipantPrincipal("p1", "参与者甲", () -> null));
    }
}
