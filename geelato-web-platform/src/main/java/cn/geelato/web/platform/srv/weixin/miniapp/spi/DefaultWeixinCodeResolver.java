package cn.geelato.web.platform.srv.weixin.miniapp.spi;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.DebugLoginCode;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppErrorCodes;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppRateLimitException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 平台默认 js_code 解析实现：读 {@code geelato.wx.miniapp} 配置直调微信 jscode2session。
 * <p>
 * 宿主应用可注册自己的 {@code WeixinCodeResolver} Bean 覆盖（如需独立配置来源或走代理）；
 * 微信 errcode 映射：40029→20006、40163→20007、45011→20015、其他非 0→20006、
 * 成功但 unionid 缺失→20010。WeixinCodeTokenAuthenticator 会把这些 CoreException 统一
 * 包装为 UnauthorizedException(20005)——符合备用通道"统一 401"定位；登录 4 接口则透传精确码。
 */
@Slf4j
public class DefaultWeixinCodeResolver implements WeixinCodeResolver {

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final WeixinMiniAppConfigurationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    public DefaultWeixinCodeResolver(WeixinMiniAppConfigurationProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /** 供测试覆写：发起 HTTP 调用并返回响应体（隔离静态外呼）。 */
    protected String fetchSessionResponse(String jsCode) throws IOException {
        String url = String.format(JSCODE2SESSION_URL, properties.getAppId(), properties.getSecret(), jsCode);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("微信接口 HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    @Override
    public String resolveUnionId(String jsCode) {
        // ===== [DEBUG-REMOVE-BEGIN] 联调调试通道（debug-enabled=true）：test_{账号}_bind → 确定性假 unionid；test_{账号}_unbind → 模拟未绑定。未来整体删除本段 =====
        if (properties.isDebugEnabled()) {
            var debug = DebugLoginCode.parse(jsCode);
            if (debug.isPresent()) {
                if (debug.get().mode() == DebugLoginCode.Mode.UNBIND) {
                    log.info("[调试] WeixinCode 通道模拟未绑定场景, account={}", debug.get().account());
                    throw new UnauthorizedException("微信身份未绑定平台账号");
                }
                String unionId = DebugLoginCode.debugUnionId(debug.get().account());
                log.warn("[调试] WeixinCode 通道使用假 unionid {}（debug-enabled=true，生产严禁开启）", unionId);
                return unionId;
            }
        }
        // ===== [DEBUG-REMOVE-END] =====
        String body;
        try {
            body = fetchSessionResponse(jsCode);
        } catch (IOException e) {
            log.error("jscode2session 调用异常", e);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.CREDENTIAL_INVALID, "登录凭证无效，请重新进入小程序");
        }
        if (StringUtils.isEmpty(body)) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.CREDENTIAL_INVALID, "登录凭证无效，请重新进入小程序");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            log.error("jscode2session 响应解析失败: {}", body);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.CREDENTIAL_INVALID, "登录凭证无效，请重新进入小程序");
        }
        int errcode = root.path("errcode").asInt(0);
        if (errcode == 40163) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.CREDENTIAL_USED, "登录凭证已被使用，请重新获取");
        }
        if (errcode == 45011) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.RATE_LIMITED, "操作过于频繁，请稍后再试");
        }
        if (errcode != 0) {
            // 40029（code 无效）及其他：统一按凭证无效处理
            log.warn("jscode2session 失败: {} - {}", errcode, root.path("errmsg").asText());
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.CREDENTIAL_INVALID, "登录凭证无效，请重新进入小程序");
        }
        String unionId = root.path("unionid").asText(null);
        if (StringUtils.isEmpty(unionId)) {
            // openid 正常但无 unionid：小程序未绑定微信开放平台
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.IDENTITY_UNAVAILABLE, "无法获取微信身份标识，请联系管理员");
        }
        return unionId;
    }
}
