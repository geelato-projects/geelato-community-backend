package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.m.security.entity.AuthCodeParams;
import cn.geelato.meta.User;
import cn.geelato.web.platform.m.security.enums.AuthCodeAction;
import cn.geelato.web.platform.m.security.enums.ValidTypeEnum;
import cn.geelato.web.platform.m.settings.enums.MessageSendStatus;
import cn.geelato.web.platform.m.settings.service.MessageService;
import cn.geelato.web.platform.utils.AuthCodeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author diabl
 */
@Component
@Slf4j
public class AuthCodeService {
    private static final int CODE_EXPIRATION_TIME = 5;
    private static final String CONFIG_KEY_TEMPLATE_CODE = "mobileTemplateAutoCode";
    @Autowired
    @Qualifier("primaryDao")
    public Dao dao;
    @Autowired
    private EmailService emailService;
    @Autowired
    private AliMobileService aliMobileService;
    @Lazy
    @Autowired
    private MessageService messageService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 生成验证码
     * <p>
     * 根据提供的表单信息，生成验证码并处理相关逻辑。
     *
     * @param form 包含验证码生成所需信息的表单对象
     * @return 如果验证码生成成功，则返回true；否则返回false
     * @throws NoSuchFieldException   如果在访问用户对象属性时找不到对应的字段，则抛出该异常
     * @throws IllegalAccessException 如果在访问用户对象属性时没有访问权限，则抛出该异常
     */
    public boolean generateUser(AuthCodeParams form) throws NoSuchFieldException, IllegalAccessException {
        // 用户
        User user = dao.queryForObject(User.class, form.getUserId());
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        // 需要查询用户信息的情况
        String[] needUser = {AuthCodeAction.VALIDATEUSER.name()};
        if (Arrays.binarySearch(needUser, form.getAction().toUpperCase(Locale.ENGLISH)) > -1) {
            form.setPrefix(user.getMobilePrefix());
            String label = ValidTypeEnum.getLabel(form.getValidType());
            if (Strings.isNotBlank(label)) {
                Class<?> clazz = user.getClass();
                Field labelField = clazz.getDeclaredField(label);
                labelField.setAccessible(true);
                form.setValidBox((String) labelField.get(user));
            }
        }
        // 发送验证码
        return generateAuth(form);
    }

    public boolean generateAuth(AuthCodeParams form) {
        String redisKey = form.getRedisKey();
        if (Strings.isBlank(redisKey)) {
            return false;
        }
        // 验证方式不能为空
        if (Strings.isBlank(form.getValidBox())) {
            return false;
        }
        // 邮件、手机号码
        if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
            messageService.updateReceiver(form.getMessage(), form.getPrefix() + form.getValidBox());
        } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
            messageService.updateReceiver(form.getMessage(), form.getValidBox());
        }
        // 验证码
        String authCode = AuthCodeUtil.sixDigitNumber();
        form.setAuthCode(authCode);
        log.info("authCode：" + authCode);
        // 加密
        String saltCode = form.getRedisValue();
        if (Strings.isBlank(saltCode)) {
            return false;
        }
        // 发送信息
        boolean sendAuthCode = action(form);
        if (!sendAuthCode) {
            return false;
        }
        // 存入缓存中
        redisTemplate.opsForValue().set(redisKey, saltCode, CODE_EXPIRATION_TIME, TimeUnit.MINUTES);
        // 保存信息
        messageService.updateContent(form.getMessage(), "Geelato 验证码", form.getAuthCode(), MessageSendStatus.SUCCESS.getValue());

        return true;
    }

    public boolean action(AuthCodeParams form) {
        String action = form.getAction().toUpperCase(Locale.ENGLISH);
        if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
            return sendMobile(form.getPrefix(), form.getValidBox(), form.getAuthCode());
        } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
            return sendEmail(form.getValidBox(), "Geelato 验证码", form.getAuthCode());
        }
        return true;
    }

    public boolean sendEmail(String to, String subject, String authCode) {
        String text = String.format("<div style=\"font-family: 宋体; sans-serif; color: #333;\">\n" +
                "  <p><span style=\"font-weight:bold; font-size:18px;\">您的验证码是：<strong style=\"color: #007BFF;\">%s</strong></span><span>，此验证码请在 %d 分钟内使用。</span></p>\n" +
                "  <p style=\"color: #FF0000;\">请勿将此验证码告知他人。</p>\n" +
                "  <p>如果您未请求此验证码，请立即联系我们的客服团队。</p>\n" +
                "  <p>感谢您使用【Geelato】！</p>\n" +
                "</div>", authCode, CODE_EXPIRATION_TIME);
        return emailService.sendHtmlMail(to, subject, text);
    }

    public boolean sendMobile(String mobilePrefix, String mobilePhone, String authCode) {
        String phoneNumbers = mobilePhone;
        if (Strings.isNotBlank(mobilePrefix) && !"+86".equals(mobilePrefix)) {
            phoneNumbers = mobilePrefix + phoneNumbers;
        }
        try {
            Map<String, Object> params = new HashedMap<>();
            params.put("code", authCode);
            return aliMobileService.sendMobile(CONFIG_KEY_TEMPLATE_CODE, phoneNumbers, params);
        } catch (Exception e) {
            log.error("发送短信时发生异常", e);
        }
        return false;
    }

    /**
     * 验证码验证
     * <p>
     * 验证提供的验证码是否与缓存中的验证码一致。
     *
     * @param form 包含验证码验证所需信息的AuthCodeParams对象
     * @return 如果提供的验证码与缓存中的验证码一致，则返回true；否则返回false
     */
    public boolean validate(AuthCodeParams form) {
        String redisKey = form.getRedisKey();
        if (Strings.isBlank(redisKey) || Strings.isBlank(form.getAuthCode())) {
            return false;
        }
        // 加密
        String saltCode = form.getRedisValue();
        // 获取缓存
        String redisCode = (String) redisTemplate.opsForValue().get(redisKey);
        log.info("redisKey-redisCode: " + redisKey + "[" + redisCode + "]");
        return saltCode.equals(redisCode);
    }


}
