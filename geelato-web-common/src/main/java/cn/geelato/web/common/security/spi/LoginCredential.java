package cn.geelato.web.common.security.spi;

/**
 * 会话建立的登录凭证描述。type 取值需与拦截器建立 Shiro 会话的既有 Realm 语义对应
 * （全新 Realm 类型属框架层演进，不在此列）。
 *
 * @param type       凭证类型常量（见 {@link TokenAuthenticator} 各实现）
 * @param principal  主体标识（登录名 / 扩展键 / accessToken / system）
 * @param credential 凭证（密码 / 扩展键 / accessToken / 固定密钥）
 */
public record LoginCredential(String type, String principal, String credential) {

    public static final String TYPE_USERNAME_PASSWORD = "usernamePassword";
    public static final String TYPE_WEIXIN_UNION_ID = "weixinUnionId";
    public static final String TYPE_WEIXIN_WORK_USER_ID = "weixinWorkUserId";
    public static final String TYPE_OAUTH2 = "oauth2";
    public static final String TYPE_SYSTEM_TOKEN = "systemToken";
}
