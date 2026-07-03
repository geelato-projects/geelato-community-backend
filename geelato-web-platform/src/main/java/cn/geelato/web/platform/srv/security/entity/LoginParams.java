package cn.geelato.web.platform.srv.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
@Setter
public class LoginParams {
    private String username;
    private String password;
    private String org;
    private String tenant;
    private String suffix;
}
