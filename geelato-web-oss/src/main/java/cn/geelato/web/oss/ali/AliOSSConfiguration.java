package cn.geelato.web.oss.ali;

import cn.geelato.web.oss.GenerateObjectNameFn;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AliOSSConfiguration {
    private String accessKeyId;
    private String accessKeySecret;
    private String endPoint;
    private String bucketName ;
    private String region;
    private GenerateObjectNameFn generateObjectNameFn;

}
