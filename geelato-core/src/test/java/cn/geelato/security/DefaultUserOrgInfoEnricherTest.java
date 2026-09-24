package cn.geelato.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DefaultUserOrgInfoEnricher} 与 {@link OrgProvider} 公司信息补齐单测。
 * <p>
 * 覆盖两条链路：
 * 1. 真实链路（DefaultOrgProvider + 内存快照）：enrich(User)/enrich(UserOrg) 后
 *    companyId/companyName/extendId 按所属公司回填，兜底链取顶层祖先；
 * 2. 接口 default 方法推导：仅实现抽象方法的最小 OrgProvider，
 *    getCompanyName/getCompanyExtendId 也能经 getCompanyId+getOrg 正确推导，
 *    保证兄弟仓库自有实现无需改动即获得新能力。
 */
@DisplayName("DefaultUserOrgInfoEnricher：公司信息（含 extendId）补齐")
class DefaultUserOrgInfoEnricherTest {

    private static final String ROOT = "root1";
    private static final String COMP1 = "comp1";
    private static final String DEPT_A = "deptA";
    private static final String SECT_A1 = "sectA1";
    private static final String DEPT_X = "deptX";

    private final Map<String, Org> orgById = new HashMap<>();
    private DefaultUserOrgInfoEnricher enricher;
    private DefaultOrgProvider defaultProvider;

    @BeforeEach
    void setUp() {
        orgById.put(ROOT, org(ROOT, null, "root", "根组织", "RE"));
        orgById.put(COMP1, org(COMP1, ROOT, "company", "公司一", "E1"));
        orgById.put(DEPT_A, org(DEPT_A, COMP1, "department", "部门甲", null));
        orgById.put(SECT_A1, org(SECT_A1, DEPT_A, "section", "科室甲1", null));
        orgById.put(DEPT_X, org(DEPT_X, ROOT, "department", "部门丁", null));
        defaultProvider = new DefaultOrgProvider(() -> OrgSnapshot.from(orgById));
        defaultProvider.refresh();
        enricher = new DefaultUserOrgInfoEnricher(defaultProvider);
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
    @DisplayName("enrich(User)：按所属公司回填 companyName/extendId/buId/buName/deptId")
    void enrichUserWithCompany() {
        User user = new User();
        user.setOrgId(SECT_A1);
        enricher.enrich(user);
        assertEquals(SECT_A1, user.getOrgId());
        assertEquals("科室甲1", user.getOrgName());
        assertEquals(COMP1, user.getCompanyId());
        assertEquals("公司一", user.getCompanyName());
        assertEquals("E1", user.getExtendId());
        assertEquals(COMP1, user.getBuId());
        assertEquals("公司一", user.getBuName());
        // 科室挂靠：deptId 取上级部门
        assertEquals(DEPT_A, user.getDeptId());
    }

    @Test
    @DisplayName("enrich(User)：无 company 链兜底顶层祖先")
    void enrichUserFallbackToTopmost() {
        User user = new User();
        user.setOrgId(DEPT_X);
        enricher.enrich(user);
        assertEquals(ROOT, user.getCompanyId());
        assertEquals("根组织", user.getCompanyName());
        assertEquals("RE", user.getExtendId());
    }

    @Test
    @DisplayName("enrich(UserOrg)：deptId/companyId/extendId 同步回填")
    void enrichUserOrg() {
        UserOrg userOrg = new UserOrg();
        userOrg.setOrgId(SECT_A1);
        enricher.enrich(userOrg);
        assertEquals("科室甲1", userOrg.getName());
        assertEquals(DEPT_A, userOrg.getDeptId());
        assertEquals(COMP1, userOrg.getCompanyId());
        assertEquals("E1", userOrg.getExtendId());
    }

    @Test
    @DisplayName("DefaultOrgProvider：getCompanyName/getCompanyExtendId 与 getCompanyId 同源")
    void providerCompanyMethods() {
        assertEquals("公司一", defaultProvider.getCompanyName(SECT_A1));
        assertEquals("E1", defaultProvider.getCompanyExtendId(SECT_A1));
        // 兜底链
        assertEquals("根组织", defaultProvider.getCompanyName(DEPT_X));
        assertEquals("RE", defaultProvider.getCompanyExtendId(DEPT_X));
        // 未知组织
        assertEquals("", defaultProvider.getCompanyName("no-such-org"));
        assertNull(defaultProvider.getCompanyExtendId("no-such-org"));
    }

    @Test
    @DisplayName("接口 default 方法：最小实现也能推导 getCompanyName/getCompanyExtendId")
    void orgProviderDefaultMethods() {
        OrgProvider minimal = new OrgProvider() {
            @Override
            public Org getOrg(String orgId) {
                return orgById.get(orgId);
            }

            @Override
            public String getDeptId(String orgId) {
                return DEPT_A;
            }

            @Override
            public String getCompanyId(String orgId) {
                return COMP1;
            }

            @Override
            public void refresh() {
            }
        };
        assertEquals("公司一", minimal.getCompanyName(SECT_A1));
        assertEquals("E1", minimal.getCompanyExtendId(SECT_A1));
        // getCompanyId 为空时 default 方法不产生伪值
        OrgProvider emptyCompany = new OrgProvider() {
            @Override
            public Org getOrg(String orgId) {
                return orgById.get(orgId);
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
        assertEquals("", emptyCompany.getCompanyName(SECT_A1));
        assertNull(emptyCompany.getCompanyExtendId(SECT_A1));
    }
}
