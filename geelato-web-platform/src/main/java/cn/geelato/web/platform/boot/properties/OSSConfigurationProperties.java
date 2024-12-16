package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.oss")
public class OSSConfigurationProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private String endPoint;
    private String bucketName;
    private String region;
}
