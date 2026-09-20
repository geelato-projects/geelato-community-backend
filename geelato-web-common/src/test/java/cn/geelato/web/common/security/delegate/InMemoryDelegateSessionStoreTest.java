package cn.geelato.web.common.security.delegate;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内委托代办会话存储的基本行为（TTL 过期与容量护栏由 LocalBoundedCache 统一托管，
 * 此处只验证 Store SPI 的读写删与按代理人查询语义）。
 */
class InMemoryDelegateSessionStoreTest {

    private final InMemoryDelegateSessionStore store = new InMemoryDelegateSessionStore();

    private DelegateSession session(String originUserId, String targetLoginName) {
        return new DelegateSession(originUserId, "originLogin", "originName",
                "targetId", targetLoginName, "targetName", "geelato");
    }

    @Test
    void putGetRemoveRoundTrip() {
        store.put("Bearer t1", session("u1", "targetA"));
        assertEquals("targetA", store.get("Bearer t1").getTargetLoginName());

        store.remove("Bearer t1");
        assertNull(store.get("Bearer t1"));
    }

    @Test
    void nullOrEmptyKeyIsRejected() {
        assertNull(store.get(null));
        assertNull(store.get(""));
        store.put(null, session("u1", "targetA"));
        store.put("", session("u1", "targetA"));
        assertNull(store.get(""));
    }

    @Test
    void removeByOriginUserEvictsOnlyMatchingSessions() {
        store.put("Bearer t1", session("u1", "targetA"));
        store.put("Bearer t2", session("u1", "targetB"));
        store.put("Bearer t3", session("u2", "targetC"));

        store.removeByOriginUser("u1");

        assertNull(store.get("Bearer t1"));
        assertNull(store.get("Bearer t2"));
        assertEquals("targetC", store.get("Bearer t3").getTargetLoginName());
    }

    @Test
    void queryByOriginUserFiltersCorrectly() {
        store.put("Bearer t1", session("u1", "targetA"));
        store.put("Bearer t2", session("u2", "targetB"));

        Collection<DelegateSession> sessions = store.queryByOriginUser("u1");

        assertEquals(1, sessions.size());
        assertEquals("targetA", sessions.iterator().next().getTargetLoginName());
        assertTrue(store.queryByOriginUser("nobody").isEmpty());
    }
}
