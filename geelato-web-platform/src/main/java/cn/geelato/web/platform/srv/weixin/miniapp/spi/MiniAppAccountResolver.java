package cn.geelato.web.platform.srv.weixin.miniapp.spi;

import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppAccount;

import java.util.Optional;

/**
 * 手机号 → 平台账号解析 SPI：pre-bind 时将微信授权手机号匹配到已开通账号。
 * <p>
 * 平台默认实现按 {@code platform_user.mobile_phone} 精确匹配启用中账号（多条取第一条，
 * ORDER BY create_at）；宿主应用可覆盖（如按业务侧"已开通账号"表匹配）。
 * 定义同类型 Bean 即覆盖（SecurityProviderConfiguration 的 ConditionalOnMissingBean 装配惯例）。
 */
public interface MiniAppAccountResolver {

    /**
     * @param phone 微信授权手机号（purePhoneNumber，平台已从 getuserphonenumber 解出）
     * @return 匹配到的账号；空 Optional 表示未匹配（pre-bind 返回 matched=false，业务态非错误）
     */
    Optional<MiniAppAccount> resolve(String phone);
}
