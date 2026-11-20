package cn.geelato.web.platform.srv.weixin.miniapp;

/**
 * 微信小程序（用户绑定与鉴权）模块错误码常量：20xxx 认证段，已登记平台 error-codes.md。
 * <p>
 * 抛出方式：按场景构造 {@link MiniAppUnauthorizedException}（401 族 20006-20011）、
 * {@link MiniAppAccountDisabledException}（403/20012）、{@link MiniAppBindConflictException}
 * （409 族 20013/20014）、{@link MiniAppRateLimitException}（429/20015），提示语传入构造器。
 */
public final class MiniAppErrorCodes {

    // ---- 401 族（MiniAppUnauthorizedException）----
    /** 登录凭证（js_code）无效 */
    public static final int CREDENTIAL_INVALID = 20006;
    /** 登录凭证已被使用 */
    public static final int CREDENTIAL_USED = 20007;
    /** 手机号授权失败 */
    public static final int PHONE_AUTH_FAILED = 20008;
    /** 绑定确认票据无效/过期 */
    public static final int TICKET_INVALID = 20009;
    /** 无法获取微信身份（unionid 缺失） */
    public static final int IDENTITY_UNAVAILABLE = 20010;
    /** 微信未绑定账号 */
    public static final int NOT_BOUND = 20011;

    // ---- 403 / 409 / 429 ----
    /** 账号已停用（MiniAppAccountDisabledException） */
    public static final int ACCOUNT_DISABLED = 20012;
    /** 该微信已绑定其他账号（MiniAppBindConflictException） */
    public static final int UNION_ID_OCCUPIED = 20013;
    /** 该账号已绑定其他微信（MiniAppBindConflictException） */
    public static final int ACCOUNT_ALREADY_BOUND = 20014;
    /** 操作过于频繁（MiniAppRateLimitException） */
    public static final int RATE_LIMITED = 20015;

    private MiniAppErrorCodes() {
    }
}
