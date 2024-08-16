package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @date 2024/6/5 15:28
 */
@Getter
@Setter
public class AliEmail {
    private String host;
    private int port = -1;
    private String username;
    private String password;
}
