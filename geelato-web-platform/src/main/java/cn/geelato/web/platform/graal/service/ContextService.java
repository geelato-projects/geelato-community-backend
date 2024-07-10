package cn.geelato.web.platform.graal.service;

import cn.geelato.web.platform.PlatformContext;
import cn.geelato.web.platform.Tenant;
import cn.geelato.core.env.entity.User;
import cn.geelato.core.graal.GraalService;

@GraalService(name = "ctx",built = "true")
public class ContextService {
    public Tenant getCurrentTenant(){
        return PlatformContext.getCurrentTenant();
    }

    public User getCurrentUser(){
        return PlatformContext.getCurrentUser();
    }
}
