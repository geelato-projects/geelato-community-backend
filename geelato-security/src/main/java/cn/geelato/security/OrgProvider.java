package cn.geelato.security;

import java.util.Collections;
import java.util.List;

/**
 * 组织信息提供者。
 * 面向消费方暴露只读查询能力，具体缓存与加载策略由实现类负责。
 */
public interface OrgProvider {

    Org getOrg(String orgId);

    /**
     * 全量组织（供 {@link SecurityProvider} 桥接实现推导组织树/子部门等反向查询）。
     * 快照式实现应覆写；直连式实现可不支持（返回空列表，桥接的组织树方法随之返回空）。
     */
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

    default String getBuId(String orgId) {
        return getCompanyId(orgId);
    }

    void refresh();
}
