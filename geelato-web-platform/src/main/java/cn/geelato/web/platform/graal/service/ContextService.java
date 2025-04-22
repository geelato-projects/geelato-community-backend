package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

@GraalService(name = "ctx",built = "true")
public class ContextService {
    public Tenant getCurrentTenant(){
        return SecurityContext.getCurrentTenant();
    }

    public User getCurrentUser(){
        return SecurityContext.getCurrentUser();
    }
}
