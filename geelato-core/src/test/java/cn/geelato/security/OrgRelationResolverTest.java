package cn.geelato.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link OrgRelationResolver} 公司归属解析单测。
 * <p>
 * 语义：沿父链找第一个 type=company 的组织；链上无 company 时回退取最顶层祖先，
 * companyId 与 extendId 必须始终描述同一个组织（供数据权限占位符
 * #currentUser.companyId# / #currentUser.extendId# 同源取值）。
 */
@DisplayName("OrgRelationResolver：公司归属解析（company优先+顶层兜底）")
class OrgRelationResolverTest {

    private static final String ROOT = "root1";
    private static final String COMP1 = "comp1";
    private static final String COMP2 = "comp2";
    private static final String COMP3 = "comp3"; // type 大小写混合
    private static final String DEPT_A = "deptA";
    private static final String SECT_A1 = "sectA1";
    private static final String DEPT_B = "deptB";
    private static final String DEPT_C = "deptC";
    private static final String DEPT_X = "deptX";
    private static final String ORPHAN = "orphan";

    private OrgRelationResolver resolver;

    @BeforeEach
    void setUp() {
        Map<String, Org> orgById = new HashMap<>();
        orgById.put(ROOT, org(ROOT, null, "root", "根组织", "RE"));
        orgById.put(COMP1, org(COMP1, ROOT, "company", "公司一", "E1"));
        orgById.put(DEPT_A, org(DEPT_A, COMP1, "department", "部门甲", null));
        orgById.put(SECT_A1, org(SECT_A1, DEPT_A, "section", "科室甲1", null));
        // 嵌套公司：deptB 挂在 comp2 下，取最近的祖先公司 comp2 而非 comp1
        orgById.put(COMP2, org(COMP2, COMP1, "company", "公司二", "E2"));
        orgById.put(DEPT_B, org(DEPT_B, COMP2, "department", "部门乙", null));
        // type 值大小写混合也应命中
        orgById.put(COMP3, org(COMP3, ROOT, "Company", "公司三", "E3"));
        orgById.put(DEPT_C, org(DEPT_C, COMP3, "department", "部门丙", null));
        // 挂在 root 下且链上无 company：兜底取顶层祖先
        orgById.put(DEPT_X, org(DEPT_X, ROOT, "department", "部门丁", null));
        // 断链：pid 指向不存在的组织，兜底取自身
        orgById.put(ORPHAN, org(ORPHAN, "missing-parent", "department", "孤儿部门", "OE"));
        resolver = new OrgRelationResolver(orgById);
    }

    private static Org org(String id, String pid, String type, String name, String extendId) {
        Org o = new Org();
        o.setOrgId(id);
        o.setPid(pid);
        o.setType(type);
        o.setName(name);
        o.setExtendId(extendId);
        return o;
    }

    @Test
    @DisplayName("链中命中 company：部门/科室取所属公司 id 与 extendId")
    void resolveCompanyInChain() {
        assertEquals(COMP1, resolver.resolveCompanyId(DEPT_A));
        assertEquals("E1", resolver.resolveCompanyExtendId(DEPT_A));
        assertEquals(COMP1, resolver.resolveCompanyId(SECT_A1));
        assertEquals("E1", resolver.resolveCompanyExtendId(SECT_A1));
    }

    @Test
    @DisplayName("嵌套公司取最近祖先：deptB 归属 comp2 而非 comp1")
    void resolveNearestCompany() {
        assertEquals(COMP2, resolver.resolveCompanyId(DEPT_B));
        assertEquals("E2", resolver.resolveCompanyExtendId(DEPT_B));
    }

    @Test
    @DisplayName("自身即 company：直接返回自身")
    void resolveSelfCompany() {
        assertEquals(COMP1, resolver.resolveCompanyId(COMP1));
        assertEquals("E1", resolver.resolveCompanyExtendId(COMP1));
    }

    @Test
    @DisplayName("type 大小写不敏感：'Company' 命中")
    void resolveCaseInsensitiveType() {
        assertEquals(COMP3, resolver.resolveCompanyId(DEPT_C));
        assertEquals("E3", resolver.resolveCompanyExtendId(DEPT_C));
    }

    @Test
    @DisplayName("链上无 company：兜底取最顶层祖先的 id 与 extendId")
    void resolveFallbackToTopmostAncestor() {
        assertEquals(ROOT, resolver.resolveCompanyId(DEPT_X));
        assertEquals("RE", resolver.resolveCompanyExtendId(DEPT_X));
    }

    @Test
    @DisplayName("断链：父级不存在时兜底取自身")
    void resolveFallbackToSelfOnBrokenChain() {
        assertEquals(ORPHAN, resolver.resolveCompanyId(ORPHAN));
        assertEquals("OE", resolver.resolveCompanyExtendId(ORPHAN));
    }

    @Test
    @DisplayName("组织不存在：companyId 空串、extendId 为 null、公司组织为 null")
    void resolveUnknownOrg() {
        assertEquals("", resolver.resolveCompanyId("no-such-org"));
        assertNull(resolver.resolveCompanyExtendId("no-such-org"));
        assertNull(resolver.resolveCompanyOrg("no-such-org"));
    }

    @Test
    @DisplayName("companyId 与 extendId 同源：resolveCompanyOrg 返回同一个组织对象")
    void resolveCompanyOrgIsSharedSource() {
        Org company = resolver.resolveCompanyOrg(DEPT_A);
        assertEquals(COMP1, company.getOrgId());
        assertEquals("公司一", company.getName());
        assertEquals("E1", company.getExtendId());
        // 兜底场景同样同源：无 company 链返回顶层祖先组织
        Org topmost = resolver.resolveCompanyOrg(DEPT_X);
        assertEquals(ROOT, topmost.getOrgId());
        assertEquals("RE", topmost.getExtendId());
    }
}
