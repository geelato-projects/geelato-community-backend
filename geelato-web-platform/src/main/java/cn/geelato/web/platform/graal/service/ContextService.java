package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

@GraalService(name = "ctx",built = "true", descrption = "上下文相关")
public class ContextService {
    @GraalFunction(example = "$gl.ctx.getCurrentTenant()", description = "获取当前登录上下文的租户信息")
    public Tenant getCurrentTenant(){
        return SecurityContext.getCurrentTenant();
    }

    @GraalFunction(example = "$gl.ctx.getCurrentUser()", description = "获取当前登录上下文的用户信息")
    public User getCurrentUser(){
        return SecurityContext.getCurrentUser();
    }
}
