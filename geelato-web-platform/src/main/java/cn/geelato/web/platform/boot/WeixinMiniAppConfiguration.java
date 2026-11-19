package cn.geelato.web.platform.boot;

import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.security.service.UserService;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.DefaultMiniAppAccountResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.DefaultWeixinCodeResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppAccountResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppLoginResultEnhancer;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.NoopMiniAppLoginResultEnhancer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序（用户绑定与鉴权）SPI 装配：平台提供默认实现，宿主应用定义同类型 Bean 即覆盖
 * （对齐 {@link SecurityProviderConfiguration} 的 ConditionalOnMissingBean 装配惯例）。
 *
 * <ul>
 *   <li>{@code WeixinCodeResolver}：默认直调 jscode2session（读 geelato.wx.miniapp 配置）；
 *       登录 4 接口与 WeixinCode 备用认证器共用，code2session 单点实现。</li>
 *   <li>{@code MiniAppAccountResolver}：默认按 platform_user.mobile_phone 匹配启用账号取第一条；
 *       宿主可覆盖为按业务侧"已开通账号"表匹配。</li>
 *   <li>{@code MiniAppLoginResultEnhancer}：默认空实现；宿主填充角色/客户名等业务字段。</li>
 * </ul>
 */
@Configuration
public class WeixinMiniAppConfiguration {

    @Bean
    @ConditionalOnMissingBean(WeixinCodeResolver.class)
    public WeixinCodeResolver weixinCodeResolver(WeixinMiniAppConfigurationProperties properties) {
        return new DefaultWeixinCodeResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(MiniAppAccountResolver.class)
    public MiniAppAccountResolver miniAppAccountResolver(UserService userService) {
        return new DefaultMiniAppAccountResolver(userService);
    }

    @Bean
    @ConditionalOnMissingBean(MiniAppLoginResultEnhancer.class)
    public MiniAppLoginResultEnhancer miniAppLoginResultEnhancer() {
        return new NoopMiniAppLoginResultEnhancer();
    }
}
