package cn.geelato.exception.engine;

import cn.geelato.exception.engine.participant.Participant;
import cn.geelato.exception.engine.participant.ParticipantResolvers;
import cn.geelato.exception.engine.participant.ParticipantSolver;
import cn.geelato.security.SecurityProvider;
import cn.geelato.security.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantSolverTest {

    private static User user(String id, String name) {
        User u = new User();
        u.setUserId(id);
        u.setUserName(name);
        return u;
    }

    private final SecurityProvider provider = new SecurityProvider() {
        @Override
        public User getUserById(String userId) {
            return "u1".equals(userId) ? user("u1", "张三") : null;
        }

        @Override
        public List<User> queryUsersOfRole(String roleCode) {
            return "cwjl".equals(roleCode) ? List.of(user("u2", "李四"), user("u3", "王五")) : List.of();
        }

        @Override
        public List<User> queryUsersOfOrgRole(String orgId, String roleCode) {
            if ("ORG1".equals(orgId) && "cwjl".equals(roleCode)) {
                return List.of(user("u4", "赵六"));
            }
            if ("ORG-FGS".equals(orgId) && "fgs_zjl".equals(roleCode)) {
                return List.of(user("u5", "钱总"));
            }
            return List.of();
        }

        @Override
        public List<cn.geelato.security.Org> queryAncestorOrgs(String orgId) {
            if ("ORG-TEAM".equals(orgId)) {
                return List.of(org("ORG-FGS", "ORG-HQ"), org("ORG-HQ", null));
            }
            return List.of();
        }
    };

    private static cn.geelato.security.Org org(String orgId, String pid) {
        cn.geelato.security.Org o = new cn.geelato.security.Org();
        o.setOrgId(orgId);
        o.setPid(pid);
        o.setName("n-" + orgId);
        o.setTenantCode("geelato");
        return o;
    }

    private final ParticipantSolver solver = new ParticipantSolver(ParticipantResolvers.defaults(provider));

    @Test
    void resolvesMixedHandlersAndDedups() {
        String selector = "{\"handlers\":[" +
                "{\"type\":\"USER\",\"userId\":\"u1\"}," +
                "{\"type\":\"ROLE\",\"roleCode\":\"cwjl\"}," +
                "{\"type\":\"USER\",\"userId\":\"u1\"}]," +
                "\"cc\":[{\"type\":\"BIZ_FIELD\"}]}";
        ParticipantSolver.Solution solution = solver.solve(selector, Map.of("ownerId", "owner9"));
        assertEquals(3, solution.handlers().size());
        assertEquals(1, solution.cc().size());
        assertEquals("张三", solution.handlers().get(0).getName());
        assertEquals("owner9", solution.cc().get(0).getId());
    }

    @Test
    void deptRoleResolvesFromBizOrg() {
        String selector = "{\"handlers\":[{\"type\":\"DEPT_ROLE\",\"roleCode\":\"cwjl\",\"org\":{\"scope\":\"BIZ_ORG\"}}]}";
        ParticipantSolver.Solution solution = solver.solve(selector, Map.of("ownerOrgId", "ORG1"));
        assertEquals(1, solution.handlers().size());
        assertEquals("u4", solution.handlers().get(0).getId());
    }

    @Test
    void orgTreeUpResolvesAncestorLevelRole() {
        // 升级链 E1：业务组织向上 1 层（分公司）+ 分公司总经理角色
        String selector = "{\"handlers\":[{\"type\":\"ORG_TREE_UP\",\"level\":1,\"roleCode\":\"fgs_zjl\",\"org\":{\"scope\":\"BIZ_ORG\"}}]}";
        ParticipantSolver.Solution level1 = solver.solve(selector, Map.of("ownerOrgId", "ORG-TEAM"));
        assertEquals(1, level1.handlers().size());
        assertEquals("u5", level1.handlers().get(0).getId());

        // level 越界（组织层级不足）取根组织，无匹配角色则空
        String deep = "{\"handlers\":[{\"type\":\"ORG_TREE_UP\",\"level\":9,\"roleCode\":\"none\",\"org\":{\"scope\":\"BIZ_ORG\"}}]}";
        assertTrue(solver.solve(deep, Map.of("ownerOrgId", "ORG-TEAM")).handlers().isEmpty());

        // 无祖先链或 provider 未接入时空结果
        assertTrue(solver.solve(selector, Map.of("ownerOrgId", "ORG-X")).handlers().isEmpty());
    }

    @Test
    void unknownTypeSkippedAndEmptySelectorYieldsEmpty() {
        ParticipantSolver.Solution solution = solver.solve(
                "{\"handlers\":[{\"type\":\"GHOST\"}]}", Map.of());
        assertTrue(solution.handlers().isEmpty());
        assertTrue(solver.solve(null, Map.of()).handlers().isEmpty());
    }

    @Test
    void providerAbsentDegradesGracefully() {
        ParticipantSolver bare = new ParticipantSolver(ParticipantResolvers.defaults(null));
        ParticipantSolver.Solution solution = bare.solve(
                "{\"handlers\":[{\"type\":\"ROLE\",\"roleCode\":\"cwjl\"},{\"type\":\"USER\",\"userId\":\"u1\"}]}",
                Map.of());
        assertEquals(1, solution.handlers().size());
        assertEquals("u1", solution.handlers().get(0).getId());
    }
}
