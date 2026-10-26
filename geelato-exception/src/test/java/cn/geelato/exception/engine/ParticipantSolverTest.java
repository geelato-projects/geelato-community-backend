package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.Hit;
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
        // BIZ_FIELD 必须显式声明 field（ownerId 已出契约，作为 extras 字段按名取）
        String selector = "{\"handlers\":[" +
                "{\"type\":\"USER\",\"userId\":\"u1\"}," +
                "{\"type\":\"ROLE\",\"roleCode\":\"cwjl\"}," +
                "{\"type\":\"USER\",\"userId\":\"u1\"}]," +
                "\"cc\":[{\"type\":\"BIZ_FIELD\",\"field\":\"ownerId\"}]}";
        ParticipantSolver.Solution solution = solver.solve(selector, Hit.fromMap(Map.of("ownerId", "owner9")));
        assertEquals(3, solution.handlers().size());
        assertEquals(1, solution.cc().size());
        assertEquals("张三", solution.handlers().get(0).getName());
        assertEquals("owner9", solution.cc().get(0).getId());
    }

    @Test
    void pluralSelectorKeysResolveBatches() {
        // userIds/roleCodes 复数形式与单数键同义，多值展开求解
        String selector = "{\"handlers\":[{\"type\":\"USER\",\"userIds\":[\"u1\",\"u2\"]}],"
                + "\"cc\":[{\"type\":\"ROLE\",\"roleCodes\":[\"cwjl\",\"none\"]}]}";
        ParticipantSolver.Solution solution = solver.solve(selector, Hit.fromMap(Map.of()));
        assertEquals(2, solution.handlers().size());
        assertEquals("u1", solution.handlers().get(0).getId());
        assertEquals("u2", solution.handlers().get(1).getId());
        // queryUsersOfRole("none") 为空，cc 只解出 cwjl 的两人
        assertEquals(2, solution.cc().size());
    }

    @Test
    void deptRoleBizOrgScopeRetired() {
        // BIZ_ORG 基点已随 ownerOrgId 出契约下线：该 scope 不再解析组织，求解为空
        String selector = "{\"handlers\":[{\"type\":\"DEPT_ROLE\",\"roleCode\":\"cwjl\",\"org\":{\"scope\":\"BIZ_ORG\"}}]}";
        assertTrue(solver.solve(selector, Hit.fromMap(Map.of("ownerOrgId", "ORG1"))).handlers().isEmpty());
    }

    @Test
    void orgTreeUpResolvesAncestorLevelRole() {
        // 升级链 E1：组织基点取自命中字段（ownerOrgId 作 extras，经 BIZ_FIELD 通用机制）
        // 向上 1 层（分公司）+ 分公司总经理角色
        String selector = "{\"handlers\":[{\"type\":\"ORG_TREE_UP\",\"level\":1,\"roleCode\":\"fgs_zjl\","
                + "\"org\":{\"scope\":\"BIZ_FIELD\",\"field\":\"ownerOrgId\"}}]}";
        ParticipantSolver.Solution level1 = solver.solve(selector, Hit.fromMap(Map.of("ownerOrgId", "ORG-TEAM")));
        assertEquals(1, level1.handlers().size());
        assertEquals("u5", level1.handlers().get(0).getId());

        // level 越界（组织层级不足）取根组织，无匹配角色则空
        String deep = "{\"handlers\":[{\"type\":\"ORG_TREE_UP\",\"level\":9,\"roleCode\":\"none\","
                + "\"org\":{\"scope\":\"BIZ_FIELD\",\"field\":\"ownerOrgId\"}}]}";
        assertTrue(solver.solve(deep, Hit.fromMap(Map.of("ownerOrgId", "ORG-TEAM"))).handlers().isEmpty());

        // 无祖先链或 provider 未接入时空结果
        assertTrue(solver.solve(selector, Hit.fromMap(Map.of("ownerOrgId", "ORG-X"))).handlers().isEmpty());
    }

    @Test
    void unknownTypeSkippedAndEmptySelectorYieldsEmpty() {
        ParticipantSolver.Solution solution = solver.solve(
                "{\"handlers\":[{\"type\":\"GHOST\"}]}", Hit.fromMap(Map.of()));
        assertTrue(solution.handlers().isEmpty());
        assertTrue(solver.solve(null, Hit.fromMap(Map.of())).handlers().isEmpty());
    }

    @Test
    void providerAbsentDegradesGracefully() {
        ParticipantSolver bare = new ParticipantSolver(ParticipantResolvers.defaults(null));
        ParticipantSolver.Solution solution = bare.solve(
                "{\"handlers\":[{\"type\":\"ROLE\",\"roleCode\":\"cwjl\"},{\"type\":\"USER\",\"userId\":\"u1\"}]}",
                Hit.fromMap(Map.of()));
        assertEquals(1, solution.handlers().size());
        assertEquals("u1", solution.handlers().get(0).getId());
    }

    @Test
    void bizFieldResolvesNamedBusinessFields() {
        // 业务字段取人：管道脚本在 hits 里返回 operatorId（单人）与 salesmanIds（多人，进 extras）
        String selector = "{\"handlers\":[{\"type\":\"BIZ_FIELD\",\"fields\":[\"operatorId\",\"salesmanIds\"]}]}";
        Hit hit = Hit.fromMap(Map.of(
                "bizId", "ODR-1",
                "operatorId", "u1",
                "salesmanIds", List.of("u2", "u3")));
        ParticipantSolver.Solution solution = solver.solve(selector, hit);
        assertEquals(3, solution.handlers().size());
        // u1 在 provider 可查回填姓名，u2/u3 查不到原样透传（id 即姓名）
        assertEquals("张三", solution.handlers().get(0).getName());
        assertEquals("u2", solution.handlers().get(1).getId());
        assertEquals("u3", solution.handlers().get(2).getId());
    }

    @Test
    void bizFieldSupportsSingularKeyUserObjectAndDedup() {
        // field 单数键；{userId,userName} 对象值带回姓名；跨字段同人去重
        String selector = "{\"handlers\":[" +
                "{\"type\":\"BIZ_FIELD\",\"field\":\"clerk\"}," +
                "{\"type\":\"BIZ_FIELD\",\"fields\":[\"operatorId\"]}]}";
        Hit hit = Hit.fromMap(Map.of(
                "operatorId", "u2",
                "clerk", Map.of("userId", "u9", "userName", "临时岗")));
        ParticipantSolver.Solution solution = solver.solve(selector, hit);
        assertEquals(2, solution.handlers().size());
        assertEquals("u9", solution.handlers().get(0).getId());
        assertEquals("临时岗", solution.handlers().get(0).getName());
        assertEquals("u2", solution.handlers().get(1).getId());
    }

    @Test
    void deptRoleResolvesFromBizFieldOrg() {
        // 组织基点取自业务字段：订单业务员部门 ∩ 角色
        String selector = "{\"handlers\":[{\"type\":\"DEPT_ROLE\",\"roleCode\":\"cwjl\",\"org\":{\"scope\":\"BIZ_FIELD\",\"field\":\"salesmanOrgId\"}}]}";
        ParticipantSolver.Solution solution = solver.solve(selector,
                Hit.fromMap(Map.of("salesmanOrgId", "ORG1")));
        assertEquals(1, solution.handlers().size());
        assertEquals("u4", solution.handlers().get(0).getId());
    }
}
