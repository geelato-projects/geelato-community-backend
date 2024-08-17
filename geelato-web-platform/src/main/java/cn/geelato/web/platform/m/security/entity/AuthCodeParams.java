package cn.geelato.web.platform.m.security.entity;

import cn.geelato.web.platform.m.security.service.AccountService;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.Digests;
import cn.geelato.utils.Encodes;
import cn.geelato.web.platform.enums.ValidTypeEnum;
import org.springframework.util.Assert;

/**
 * @author diabl
 * @description: 验证码
 */
@Getter
@Setter
public class AuthCodeParams {
    private String action; // forgetPassword;updateMobile,updatePassword,updateEmail
    private String validType;// 验证方式，validType
    private String prefix;// 手机前缀
    private String validBox;// 手机、邮箱
    private String authCode; // 验证码
    private String userId;// 用户id，

    /**
     * 构建，缓存key值
     *
     * @return
     */
    public String getRedisKey() {
        if (Strings.isNotBlank(this.action) && Strings.isNotBlank(this.userId) && Strings.isNotBlank(this.validType)) {
            String validLabel = ValidTypeEnum.getLabel(this.validType);
            if (Strings.isNotBlank(validLabel)) {
                return String.format("%s-%s-%s", this.action, this.userId, validLabel);
            }
        }

        return null;
    }

    /**
     * 获取，缓存value值
     *
     * @return
     */
    public String getRedisValue() {
        return getRedisValue(this.authCode);
    }

    /**
     * 设置，缓存value值
     *
     * @param authCode
     * @return
     */
    public String getRedisValue(String authCode) {
        if (Strings.isNotBlank(authCode)) {
            return Encodes.encodeHex(Digests.sha1(authCode.getBytes(), this.userId.getBytes(), AccountService.HASH_INTERATIONS));
        }

        return null;
    }

    /**
     * 找回密码，设置验证码
     *
     * @param fp
     * @return
     */
    public static AuthCodeParams buildAuthCodeParams(ForgetPasswordParams fp) {
        Assert.notNull(fp, ApiErrorMsg.IS_NULL);
        AuthCodeParams ac = new AuthCodeParams();
        ac.setAction(fp.getAction());
        ac.setAuthCode(fp.getAuthCode());
        ac.setPrefix(fp.getPrefix());
        ac.setValidType(fp.getValidType());
        ac.setValidBox(fp.getValidBox());
        ac.setUserId(fp.getUserId());

        return ac;
    }
}
