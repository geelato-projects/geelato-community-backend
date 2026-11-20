package cn.geelato.web.platform.boot.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置属性类（用户绑定与鉴权模块）。
 * 配置前缀：geelato.wx.miniapp
 * <p>
 * 注意与 {@link WeixinConfigurationProperties}（geelato.wx，网站应用微信 OAuth2）不是同一套 appid/secret，勿混用。
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "geelato.wx.miniapp")
public class WeixinMiniAppConfigurationProperties {

    /** 总开关：false 时 /api/weixin/miniapp/** 统一返回 404（紧急下线收口） */
    private boolean enabled = true;

    // ===== [DEBUG-REMOVE-BEGIN] 联调调试开关：未来删除本字段（同时删除 DebugLoginCode.java 及全部 [DEBUG-REMOVE] 标记处）=====
    /**
     * 联调调试开关：true 时 code 支持 {@code test_{账号}_bind / test_{账号}_unbind} 格式，
     * 绕过微信直接模拟"已绑定/未绑定"场景（详见 DebugLoginCode）。
     * ⚠️ 生产环境必须保持 false（默认）：开启后任何人可伪造任意账号登录。
     */
    private boolean debugEnabled = false;
    // ===== [DEBUG-REMOVE-END] =====

    /** 小程序 AppID */
    private String appId;

    /** 小程序 AppSecret */
    private String secret;

    /** preBindTicket 签名密钥（HMAC-SHA256）；未配置时 pre-bind 端点不可用 */
    private String ticketKey;

    /** preBindTicket 有效期（秒），默认 300 秒 */
    private int ticketTtlSeconds = 300;

    /** 会话 token（JWTBearer）有效期（秒），默认 7 天；本地验签无服务端吊销，不宜过长 */
    private int tokenTtlSeconds = 604800;
}
