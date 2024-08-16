package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @date 2024/3/20 14:29
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
