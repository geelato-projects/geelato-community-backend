package cn.geelato.web.platform.boot.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信企业号配置属性类
 * 配置前缀：geelato.wx.work
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "geelato.wx.work")
public class WeixinWorkConfigurationProperties {
    private String apiBaseUrl = "https://qyapi.weixin.qq.com/cgi-bin";
    
    /**
     * 访问令牌缓存时间（秒），默认7200秒（2小时）
     * 企业微信访问令牌有效期为2小时，建议设置为7000秒以留出缓冲时间
     */
    private int tokenCacheTime = 7000;
}