package cn.geelato.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DefaultUserProvider} 扩展属性（{@link UserExtInfoEnricher}）接线单测：
 * refresh() 对快照内全部用户执行填充；双参构造（无填充器）路径行为不变。
 */
@DisplayName("DefaultUserProvider：UserExtInfoEnricher 快照填充")
class DefaultUserProviderExtInfoTest {

    private static User user(String id) {
        User u = new User();
        u.setUserId(id);
        u.setLoginName(id);
        return u;
    }

    private static UserSnapshotLoader loaderOf(Map<String, User> userById) {
        return enricher -> UserSnapshot.from(userById, Collections.emptyMap());
    }

    private static OrgProvider emptyOrgProvider() {
        return new OrgProvider() {
            @Override
            public Org getOrg(String orgId) {
                return null;
            }

            @Override
            public String getDeptId(String orgId) {
                return "";
            }

            @Override
            public String getCompanyId(String orgId) {
                return "";
            }

            @Override
            public void refresh() {
            }
        };
    }

    @Test
    @DisplayName("refresh()：三参构造时填充器作用于快照内全部用户")
    void refreshAppliesExtEnricherToAllUsers() {
        Map<String, User> userById = new LinkedHashMap<>();
        userById.put("u1", user("u1"));
        userById.put("u2", user("u2"));
        AtomicInteger invoked = new AtomicInteger();

        UserExtInfoEnricher extEnricher = u -> {
            invoked.incrementAndGet();
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("git.username", u.getLoginName() + "-git");
            ext.put("git.token", "pat-" + u.getUserId());
            u.setExtendInfo(ext);
        };

        DefaultUserProvider provider = new DefaultUserProvider(
                loaderOf(userById), new DefaultUserOrgInfoEnricher(emptyOrgProvider()), extEnricher);
        provider.refresh();

        assertEquals(2, invoked.get());
        assertEquals("u1-git", provider.getUser("u1").getExtendInfo().get("git.username"));
        assertEquals("pat-u2", provider.getUser("u2").getExtendInfo().get("git.token"));
    }

    @Test
    @DisplayName("refresh()：原双参构造（无填充器）行为不变，extendInfo 保持 null")
    void twoArgConstructorKeepsLegacyBehavior() {
        Map<String, User> userById = new LinkedHashMap<>();
        userById.put("u1", user("u1"));
        DefaultUserProvider provider = new DefaultUserProvider(
                loaderOf(userById), new DefaultUserOrgInfoEnricher(emptyOrgProvider()));
        provider.refresh();

        assertNull(provider.getUser("u1").getExtendInfo());
    }

    @Test
    @DisplayName("refresh()：重复刷新时填充器按新快照重新执行")
    void refreshReappliesOnEachRefresh() {
        Map<String, User> userById = new LinkedHashMap<>();
        userById.put("u1", user("u1"));
        AtomicInteger invoked = new AtomicInteger();
        UserExtInfoEnricher extEnricher = u -> {
            invoked.incrementAndGet();
            u.setExtendInfo(Collections.singletonMap("round", String.valueOf(invoked.get())));
        };

        DefaultUserProvider provider = new DefaultUserProvider(
                loaderOf(userById), new DefaultUserOrgInfoEnricher(emptyOrgProvider()), extEnricher);
        provider.refresh();
        provider.refresh();

        assertEquals(2, invoked.get());
        assertEquals("2", provider.getUser("u1").getExtendInfo().get("round"));
    }
}
