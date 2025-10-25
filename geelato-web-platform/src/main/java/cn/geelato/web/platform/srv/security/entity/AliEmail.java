package cn.geelato.web.platform.srv.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
public class AliEmail {
    private String host;
    private int port = -1;
    private String username;
    private String password;
}
