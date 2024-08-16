package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 找回密码
 * @date 2023/7/17 11:13
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
