package cn.geelato.security;

/**
 * 组织信息提供者。
 * 面向消费方暴露只读查询能力，具体缓存与加载策略由实现类负责。
 */
public interface OrgProvider {

    Org getOrg(String orgId);

    default boolean containsOrg(String orgId) {
        return getOrg(orgId) != null;
    }

    default String getOrgName(String orgId) {
        Org org = getOrg(orgId);
        return org == null ? "" : org.getName();
    }

    String getDeptId(String orgId);

    String getCompanyId(String orgId);

    default String getBuId(String orgId) {
        return getCompanyId(orgId);
    }

    void refresh();
}
