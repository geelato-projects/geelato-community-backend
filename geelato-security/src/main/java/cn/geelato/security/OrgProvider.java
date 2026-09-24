package cn.geelato.security;

import java.util.Collections;
import java.util.List;

/**
 * 组织信息提供者。
 * 面向消费方暴露只读查询能力，具体缓存与加载策略由实现类负责。
 */
public interface OrgProvider {

    Org getOrg(String orgId);

    default List<Org> getAllOrgs() {
        return Collections.emptyList();
    }

    default boolean containsOrg(String orgId) {
        return getOrg(orgId) != null;
    }

    default String getOrgName(String orgId) {
        Org org = getOrg(orgId);
        return org == null ? "" : org.getName();
    }

    String getDeptId(String orgId);

    String getCompanyId(String orgId);

    /** 组织所属公司的名称；链上无 company 时与 getCompanyId 一致，取最顶层祖先。 */
    default String getCompanyName(String orgId) {
        String companyId = getCompanyId(orgId);
        return companyId == null || companyId.isEmpty() ? "" : getOrgName(companyId);
    }

    /** 组织所属公司的 extendId，与 getCompanyId 同源；无 company 时取最顶层祖先的。 */
    default String getCompanyExtendId(String orgId) {
        String companyId = getCompanyId(orgId);
        if (companyId == null || companyId.isEmpty()) {
            return null;
        }
        Org company = getOrg(companyId);
        return company == null ? null : company.getExtendId();
    }

    default String getBuId(String orgId) {
        return getCompanyId(orgId);
    }

    void refresh();
}
