package cn.geelato.web.common.security.spi;

import cn.geelato.web.common.interceptor.UnauthorizedException;

/**
 * 微信小程序登录凭证解析 SPI：js_code → 微信唯一标识（unionid）。
 * <p>
 * 平台侧 {@code WeixinCodeTokenAuthenticator}（Authorization: WeixinCode &lt;code&gt; 备用通道）
 * 不感知任何小程序的 appid/secret 配置；由上层宿主实现本接口并注册为 Spring Bean
 * （封装自家 code2session 调用），认证器经 ObjectProvider 可选装配——无实现时
 * WeixinCode 通道统一 401，不影响其他认证方案。
 * <p>
 * 契约：解析成功返回非空 unionid（缺失即失败场景，如开放平台未绑定小程序，应抛
 * {@link UnauthorizedException}）；微信侧错误（code 无效/已消费/频控）抛
 * {@link UnauthorizedException} 并携带可排查的根因描述。认证器自带 code→unionid
 * 缓存（5 分钟，对齐 code 有效期），重复消费同一 code 不会二次打到实现方。
 */
public interface WeixinCodeResolver {

    String resolveUnionId(String jsCode);
}
