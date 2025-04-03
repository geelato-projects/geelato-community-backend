package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.SessionCtx;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.graal.utils.GraalUtils;
import cn.geelato.web.platform.m.security.enums.ValidTypeEnum;
import cn.geelato.web.platform.m.settings.entity.Message;
import cn.geelato.web.platform.m.settings.enums.MessageSendStatus;
import cn.geelato.web.platform.utils.EncryptUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Assert;

import java.util.Date;

/**
 * @author diabl
 * 验证码
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
    private Message message; // 消息对象

    /**
     * 构建并返回Redis缓存的key值
     * <p>
     * 根据当前对象的action、userId和validType属性，构建一个用于Redis缓存的key值。
     *
     * @return 如果action、userId和validType属性均不为空且validType对应的标签不为空，则返回构建的key值；否则返回null。
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
     * 获取缓存的value值
     * <p>
     * 根据当前实例的authCode获取并返回缓存的value值。
     *
     * @return 返回缓存的value值
     */
    public String getRedisValue() {
        return EncryptUtil.encryptAuthCode(this.authCode, this.userId);
    }

    /**
     * 找回密码，设置验证码
     * <p>
     * 根据传入的找回密码参数对象（fp），构建并返回一个包含验证码相关信息的AuthCodeParams对象。
     *
     * @param fp 找回密码参数对象，包含找回密码所需的各种信息
     * @return 返回包含验证码相关信息的AuthCodeParams对象
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

    public Message buildMessage() {
        SessionCtx sessionCtx = GraalUtils.getCxt();
        Message message = new Message();
        message.setSender(sessionCtx.getUserId());
        if (ValidTypeEnum.MOBILE.getValue().equals(this.getValidType())) {
            message.setReceiver(this.getPrefix() + this.getValidBox());
        } else if (ValidTypeEnum.MAIL.getValue().equals(this.getValidType())) {
            message.setReceiver(this.getValidBox());
        }
        if (Strings.isBlank(message.getReceiver())) {
            message.setReceiver(this.getUserId());
        }
        message.setSendTime(new Date());
        message.setSendMethod(ValidTypeEnum.getLabel(this.getValidType()));
        message.setSendType(this.getAction());
        message.setSendStatus(MessageSendStatus.UNSENT.getValue());
        return message;
    }
}
