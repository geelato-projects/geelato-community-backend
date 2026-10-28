package cn.geelato.alert.engine.receiver;

import cn.geelato.security.Org;
import cn.geelato.security.Page;
import cn.geelato.security.PageParam;
import cn.geelato.security.SecurityProvider;
import cn.geelato.security.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertReceiverSolverTest {

    /** fake SecurityProvider：org1={u1,u2}，role:FIN={u1,u3}，org2×role:OPR={u4}，org1 向上={rootOrg}。 */
    private SecurityProvider fakeProvider() {
        return new SecurityProvider() {
            @Override
            public User getUserById(String userId) {
                return switch (userId) {
                    case "u1" -> user("u1", "用户一", "org1");
                    case "u3" -> user("u3", "用户三", "org2");
                    default -> null;
                };
            }

            @Override
            public Page<User> pageQueryUsersOfOrg(String orgId, boolean includeSubOrgs, PageParam pageParam) {
                List<User> users = "org1".equals(orgId)
                        ? List.of(user("u1", "用户一", "org1"), user("u2", "用户二", "org1"))
                        : List.of();
                return Page.of(users, users.size(), 1, 200, false);
            }

            @Override
            public Page<User> pageQueryUsersOfRole(String roleCode, PageParam pageParam) {
                List<User> users = "FIN".equals(roleCode)
                        ? List.of(user("u1", "用户一", "org1"), user("u3", "用户三", "org2"))
                        : List.of();
                return Page.of(users, users.size(), 1, 200, false);
            }

            @Override
            public Page<User> pageQueryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs, PageParam pageParam) {
                List<User> users = "org2".equals(orgId) && "OPR".equals(roleCode)
                        ? List.of(user("u4", "用户四", "org2")) : List.of();
                return Page.of(users, users.size(), 1, 200, false);
            }

            @Override
            public List<Org> queryAncestorOrgs(String orgId) {
                return "org1".equals(orgId) ? List.of(org("rootOrg")) : List.of();
            }
        };
    }

    private static User user(String id, String name, String orgId) {
        User u = new User();
        u.setUserId(id);
        u.setUserName(name);
        u.setOrgId(orgId);
        return u;
    }

    private static Org org(String orgId) {
        Org o = new Org();
        o.setOrgId(orgId);
        return o;
    }

    private AlertReceiverSolver solver(String fallbackRoleCode) {
        return new AlertReceiverSolver(ReceiverSelectors.defaults(fakeProvider()), fakeProvider(), fallbackRoleCode);
    }

    @Test
    void nullJsonResolvesEmpty() {
        assertTrue(solver(null).solve(null, AlertResolveCtx.empty()).isEmpty());
    }

    @Test
    void legacyFollowSourceMarkerIgnored() {
        // 遗留形态（含 followSource 键）不再参与求解：无 extra 即无人
        assertTrue(solver(null).solve("{\"followSource\":true}", AlertResolveCtx.empty()).isEmpty());
    }

    @Test
    void roleAndUserSelectorsMergeAndDedup() {
        List<AlertReceiver> out = solver(null).solve(
                "{\"extra\":[{\"type\":\"USER\",\"userIds\":[\"u1\"]},{\"type\":\"ROLE\",\"roleCode\":\"FIN\"}]}",
                AlertResolveCtx.empty());
        assertEquals(List.of("u1", "u3"), ids(out));
    }

    @Test
    void bizFieldTakesOwnerFromContext() {
        List<AlertReceiver> out = solver(null).solve("{\"extra\":[{\"type\":\"BIZ_FIELD\"}]}",
                new AlertResolveCtx("owner1", "责任人", "org1"));
        assertEquals(List.of("owner1"), ids(out));
    }

    @Test
    void deptRoleWithBizOrgScopeResolvesOwnerOrg() {
        List<AlertReceiver> out = solver(null).solve(
                "{\"extra\":[{\"type\":\"DEPT_ROLE\",\"roleCode\":\"OPR\",\"org\":{\"scope\":\"BIZ_ORG\"}}]}",
                new AlertResolveCtx("owner1", "责任人", "org2"));
        assertEquals(List.of("u4"), ids(out));
    }

    @Test
    void orgTreeUpEmptyWhenAncestorHasNoRoleHolders() {
        // org1 向上一层=rootOrg，rootOrg 无 OPR 挂靠人 → 空
        assertTrue(solver(null).solve(
                "{\"extra\":[{\"type\":\"ORG_TREE_UP\",\"level\":1,\"roleCode\":\"OPR\"}]}",
                new AlertResolveCtx("owner1", "责任人", "org1")).isEmpty());
    }

    @Test
    void emptyResolutionFallsBackToConfiguredRole() {
        List<AlertReceiver> out = solver("FIN").solve("{}", AlertResolveCtx.empty());
        assertEquals(List.of("u1", "u3"), ids(out));
    }

    @Test
    void emptyResolutionWithoutFallbackStaysEmpty() {
        assertTrue(solver(null).solve("{}", AlertResolveCtx.empty()).isEmpty());
    }

    @Test
    void unknownSelectorTypeIsSkipped() {
        List<AlertReceiver> out = solver(null).solve(
                "{\"extra\":[{\"type\":\"NOPE\"},{\"type\":\"USER\",\"userIds\":[\"u9\"]}]}",
                AlertResolveCtx.empty());
        assertEquals(List.of("u9"), ids(out));
    }

    private static List<String> ids(List<AlertReceiver> receivers) {
        List<String> out = new ArrayList<>();
        for (AlertReceiver r : receivers) {
            out.add(r.getId());
        }
        return out;
    }
}
