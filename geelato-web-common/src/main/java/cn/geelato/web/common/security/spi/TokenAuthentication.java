package cn.geelato.web.common.security.spi;

import cn.geelato.security.User;

/**
 * 认证成功结果（Shiro-free）。拦截器据此统一填充 SecurityContext、建立 Shiro 会话并写上下文缓存。
 *
 * @param user        会话用户（已按 token 维度克隆，修改前无需再克隆）
 * @param password    写入 SecurityContext 的凭证（Shiro 会话建立依赖）
 * @param login       会话建立所需的登录凭证描述
 * @param trackOnline 是否计入在线用户（SystemToken 等虚拟主体为 false）
 */
public record TokenAuthentication(User user, String password, LoginCredential login, boolean trackOnline) {

    public TokenAuthentication(User user, String password, LoginCredential login) {
        this(user, password, login, true);
    }
}
