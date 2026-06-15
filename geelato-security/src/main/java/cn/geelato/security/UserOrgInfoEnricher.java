package cn.geelato.security;

/**
 * 用户组织派生信息补齐器。
 * 用于为用户及其组织关系补齐公司、部门等衍生字段。
 */
public interface UserOrgInfoEnricher {

    User enrich(User user);

    UserOrg enrich(UserOrg userOrg);
}
