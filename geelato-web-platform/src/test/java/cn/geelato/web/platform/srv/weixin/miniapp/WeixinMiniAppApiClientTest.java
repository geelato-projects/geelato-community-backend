package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.service.WeixinMiniAppApiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * [DEBUG-REMOVE] phoneCode 调试分支单测：debug-enabled=true 时 test_{手机号} 直接返回、不外呼微信。
 * （debug-enabled=false 分支会真实外呼微信，不在单测覆盖范围。）
 */
class WeixinMiniAppApiClientTest {

    @Test
    void debugPhoneCode_returnsPhoneWithoutWeChatCall() {
        WeixinMiniAppConfigurationProperties properties = new WeixinMiniAppConfigurationProperties();
        properties.setDebugEnabled(true);
        WeixinMiniAppApiClient client = new WeixinMiniAppApiClient(properties);
        assertEquals("13900001111", client.getPhoneNumber("test_13900001111"));
    }
}
