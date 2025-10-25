package cn.geelato.web.platform.srv.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class AliMobile {
    private String signName;
    private String templateCode;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
}
