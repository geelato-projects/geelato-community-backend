package cn.geelato.core;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionCtxTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    @Test
    void shouldNotFailWhenCurrentUserAndTenantAreMissing() {
        SessionCtx sessionCtx = new SessionCtx();

        assertNull(SessionCtx.getUserId());
        assertNull(SessionCtx.getUserName());
        assertNull(SessionCtx.getOrgId());
        assertNull(SessionCtx.getDefaultOrgId());
        assertNull(SessionCtx.getCurrentTenantCode());
        assertEquals(0, sessionCtx.size());
    }

    @Test
    void shouldExposeCurrentUserAndTenantWhenPresent() {
        User user = new User();
        user.setUserId("u1");
        user.setUserName("system");
        user.setOrgId("org1");
        user.setDefaultOrgId("org-default");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));

        SessionCtx sessionCtx = new SessionCtx();

        assertEquals("u1", SessionCtx.getUserId());
        assertEquals("system", SessionCtx.getUserName());
        assertEquals("org1", SessionCtx.getOrgId());
        assertEquals("org-default", SessionCtx.getDefaultOrgId());
        assertEquals("geelato", SessionCtx.getCurrentTenantCode());
        assertEquals("u1", sessionCtx.get("userId"));
        assertEquals("system", sessionCtx.get("userName"));
        assertEquals("org1", sessionCtx.get("orgId"));
        assertEquals("org-default", sessionCtx.get("defaultOrgId"));
        assertEquals("geelato", sessionCtx.get("tenantCode"));
    }
}
