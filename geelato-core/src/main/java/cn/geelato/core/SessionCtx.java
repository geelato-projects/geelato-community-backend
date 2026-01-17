package cn.geelato.core;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;

import java.util.HashMap;

/**
 * 上下文件参数，可用于sqlTemplate解析构建时的语句的默认内置参数
 * 也可用于mql命名执行时，获取上下文变量，如$ctx.userId
 * @author geemeta
 *
 */
public class SessionCtx extends HashMap<String,String> {
        public SessionCtx(){
        this.put("userId",getCurrentUser().getUserId());
        this.put("userName",getCurrentUser().getUserName());
        this.put("orgId",getCurrentUser().getOrgId());
        this.put("defaultOrgId",getCurrentUser().getDefaultOrgId());
        this.put("tenantCode",getCurrentTenantCode());
    }

    public static String getUserId(){
        return getCurrentUser().getUserId();
    }
    public static String getUserName(){
        return getCurrentUser().getUserName();
    }
    public static User getCurrentUser(){
        return SecurityContext.getCurrentUser();
    }

    public static String getCurrentTenantCode() {
        return SecurityContext.getCurrentTenant().getCode();
    }

}
