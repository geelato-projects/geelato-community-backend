package cn.geelato.security;

public interface SecurityHelper {

    User getUserByUserId(String userId);
    Org getOrgByOrgId(String orgId);
}
