package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 小程序端点匿名安全上下文拦截器：本模块 4 个端点均 @IgnoreVerify 免登录，请求处理期间
 * SecurityContext 无当前用户，ORM 保存链的审计字段填充器（PlatformFieldValueFillSupport）
 * 取不到操作人会把 updater/updaterName 覆盖为 null，触发 platform_user.updater NOT NULL 的 1048。
 * <p>
 * 本拦截器强制为无登录态的会话指定一个虚拟安全主体（userId=miniapp，systemPrincipal=true，
 * 仿 SystemToken 通道的 system 主体先例）：审计字段填充取到该身份，updater 记录为 miniapp；
 * 同时 ORM 钩子/事件链路也能取得会话身份。仅拦截 /api/weixin/miniapp/**，不影响其他端点。
 */
@Component
public class MiniAppAnonymousContextInterceptor implements HandlerInterceptor {

    public static final String VIRTUAL_USER_ID = "miniapp";
    public static final String VIRTUAL_USER_NAME = "小程序匿名会话";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ensureVirtualContext();
        return true;
    }

    /** 已有登录态（理论上不会有）则不覆盖；SecurityContext 由 SecurityContextFilter 在请求结束统一清理。 */
    static void ensureVirtualContext() {
        if (SecurityContext.getCurrentUser() == null) {
            User virtual = new User();
            virtual.setUserId(VIRTUAL_USER_ID);
            virtual.setLoginName(VIRTUAL_USER_ID);
            virtual.setUserName(VIRTUAL_USER_NAME);
            virtual.setSystemPrincipal(true);
            SecurityContext.setCurrentUser(virtual);
        }
    }
}
