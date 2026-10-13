package cn.geelato.script.core;

import cn.geelato.security.App;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import lombok.Getter;

/**
 * 执行身份快照：调用线程捕获（worker 线程无 ThreadLocal），$env.user 与 $gl.user/$gl.tenant/$gl.app
 * 及 ORM 调用的线程上下文重建共用同一份。
 */
@Getter
public class IdentitySnapshot {

    private static final IdentitySnapshot EMPTY = new IdentitySnapshot(null, null, null);

    private final User user;
    private final Tenant tenant;
    private final App app;

    private IdentitySnapshot(User user, Tenant tenant, App app) {
        this.user = user;
        this.tenant = tenant;
        this.app = app;
    }

    public static IdentitySnapshot empty() {
        return EMPTY;
    }

    public static IdentitySnapshot of(User user, Tenant tenant, App app) {
        return new IdentitySnapshot(user, tenant, app);
    }

    /** 读当前线程 SecurityContext（宿主可经 IdentitySupplier 替换来源）。 */
    public static IdentitySnapshot captureCurrent() {
        return of(SecurityContext.getCurrentUser(), SecurityContext.getCurrentTenant(), SecurityContext.getCurrentApp());
    }

    public boolean isPresent() {
        return user != null || tenant != null || app != null;
    }
}
