package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * 找回密码
 */
@Getter
@Setter
public class ForgetPasswordParams {
    private String action; // forgetPassword
    private String validType;
    private String prefix;
    private String validBox;
    private String authCode;
    private String userId;
    private String password;
}
