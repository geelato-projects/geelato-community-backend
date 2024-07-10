package cn.geelato.web.platform.m.security.entity;

/**
 * @author diabl
 * @description: 找回密码
 * @date 2023/7/17 11:13
 */

public class ForgetPasswordParams {
    private String action; // forgetPassword
    private String validType;
    private String prefix;
    private String validBox;
    private String authCode;
    private String userId;
    private String password;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getValidType() {
        return validType;
    }

    public void setValidType(String validType) {
        this.validType = validType;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getValidBox() {
        return validBox;
    }

    public void setValidBox(String validBox) {
        this.validBox = validBox;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
