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
