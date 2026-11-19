package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppPreBindResult;
import cn.geelato.web.platform.srv.weixin.miniapp.service.WeixinMiniAppLoginService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 微信小程序用户绑定与鉴权端点（免登录，靠一次性凭证自证：js_code / phoneCode / preBindTicket）。
 * <p>
 * 交互契约见《小程序用户绑定与鉴权-前端交互说明》：所有端点 POST + form/query 参数；
 * 成功响应 code=20000；失败抛 CoreException 子类（20xxx 段），由 PlatformExceptionHandler
 * 统一转 HTTP 语义码 + body 顶层 code=errorCode。总开关 geelato.wx.miniapp.enabled=false
 * 时统一 404 收口（参照 public-track 模式）。
 */
@ApiRestController("/weixin/miniapp")
@Slf4j
public class WeixinMiniAppController extends BaseController {

    private final WeixinMiniAppConfigurationProperties properties;
    private final WeixinMiniAppLoginService loginService;

    @Autowired
    public WeixinMiniAppController(WeixinMiniAppConfigurationProperties properties,
                                   WeixinMiniAppLoginService loginService) {
        this.properties = properties;
        this.loginService = loginService;
    }

    /**
     * 静默登录：js_code → unionid 反查绑定账号，返回身份对象（内含 token）。
     * 未绑定 → 401/20011（前端跳绑定页）；账号停用 → 403/20012。
     */
    @IgnoreVerify
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<MiniAppLoginResult> login(String code) {
        if (disabled()) {
            return notFound();
        }
        requireText(code, "code");
        return ApiResult.success(loginService.login(code));
    }

    /**
     * 手机号预匹配：phoneCode → 微信认证手机号 → 匹配已开通账号。
     * 返回脱敏账号信息 + 一次性绑定票据（preBindTicket）；未匹配返回 matched=false（业务态）。
     */
    @IgnoreVerify
    @RequestMapping(value = "/pre-bind", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<MiniAppPreBindResult> preBind(String phoneCode) {
        if (disabled()) {
            return notFound();
        }
        requireText(phoneCode, "phoneCode");
        return ApiResult.success(loginService.preBind(phoneCode));
    }

    /**
     * 确认绑定：新 js_code + preBindTicket；目标账号以票据内 userId 为准（不收客户端账号参数）。
     * 冲突：微信已绑他号 409/20013；账号已绑其他微信 409/20014。
     */
    @IgnoreVerify
    @RequestMapping(value = "/bind", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<MiniAppLoginResult> bind(String code, String preBindTicket) {
        if (disabled()) {
            return notFound();
        }
        requireText(code, "code");
        requireText(preBindTicket, "preBindTicket");
        return ApiResult.success(loginService.bind(code, preBindTicket));
    }

    /**
     * 解绑：js_code → unionid 定位绑定账号并清空 weixin_unionId（换绑前提）。
     */
    @IgnoreVerify
    @RequestMapping(value = "/unbind", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<Void> unbind(String code) {
        if (disabled()) {
            return notFound();
        }
        requireText(code, "code");
        loginService.unbind(code);
        return ApiResult.successNoResult();
    }

    private boolean disabled() {
        return !properties.isEnabled();
    }

    /** 总开关关闭时统一 404 收口（参照 public-track 模式），避免暴露端点存在性。 */
    private <T> ApiResult<T> notFound() {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return ApiResult.fail(404, "Not Found");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new AuthBadRequestException("参数不完整：" + name);
        }
    }
}
