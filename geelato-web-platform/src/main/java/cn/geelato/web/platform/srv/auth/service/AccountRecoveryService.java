package cn.geelato.web.platform.srv.auth.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
import cn.geelato.web.platform.srv.security.entity.AuthCodeParams;
import cn.geelato.web.platform.srv.security.entity.BindAccountRequest;
import cn.geelato.web.platform.srv.security.entity.ForgetPasswordRequest;
import cn.geelato.web.platform.srv.security.entity.ForgetValidRequest;
import cn.geelato.web.platform.srv.security.entity.ValidateUserRequest;
import cn.geelato.web.platform.srv.security.enums.ValidTypeEnum;
import cn.geelato.web.platform.srv.security.service.AuthCodeService;
import cn.geelato.web.platform.utils.EncryptUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AccountRecoveryService {
    private final Dao dao;
    private final AuthCodeService authCodeService;

    public AccountRecoveryService(@Qualifier("primaryDao") Dao dao, AuthCodeService authCodeService) {
        this.dao = dao;
        this.authCodeService = authCodeService;
    }

    public User findUserByValidBox(ForgetValidRequest request) {
        Map<String, Object> map = new HashMap<>();
        String validLabel = ValidTypeEnum.getLabel(request.getValidType());
        if (Strings.isBlank(request.getValidBox()) || Strings.isBlank(validLabel)) {
            throw new AuthBadRequestException("Parameter error");
        }
        map.put(validLabel, request.getValidBox());
        if (ValidTypeEnum.MOBILE.getValue().equals(request.getValidType())) {
            if (Strings.isBlank(request.getPrefix())) {
                throw new AuthBadRequestException("Parameter error");
            }
            map.put("mobilePrefix", request.getPrefix());
        }
        List<User> users = dao.queryList(User.class, map, null);
        if (users == null || users.size() != 1) {
            throw new AuthBadRequestException("user not found");
        }
        return users.get(0);
    }

    public void forgetPassword(ForgetPasswordRequest request) {
        if (Strings.isBlank(request.getUserId()) || Strings.isBlank(request.getPassword())) {
            throw new AuthBadRequestException("参数异常，请联系平台");
        }
        AuthCodeParams code = buildAuthCodeParams(request);
        if (!authCodeService.validate(code)) {
            throw new AuthBadRequestException("验证码错误");
        }
        User user = dao.queryForObject(User.class, request.getUserId());
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        user.setPlainPassword(request.getPassword());
        EncryptUtil.encryptPassword(user);
        dao.save(user);
    }

    public void validateUser(ValidateUserRequest request) {
        if (Strings.isBlank(request.getValidType()) || Strings.isBlank(request.getUserId()) || Strings.isBlank(request.getAuthCode())) {
            throw new AuthBadRequestException("参数异常，请联系平台");
        }
        User user = dao.queryForObject(User.class, request.getUserId());
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        if (ValidTypeEnum.PASSWORD.getValue().equals(request.getValidType())) {
            if (Strings.isNotBlank(user.getPassword()) && Strings.isNotBlank(user.getSalt())) {
                String pwd = EncryptUtil.encryptPassword(request.getAuthCode(), user.getSalt());
                if (user.getPassword().equals(pwd)) {
                    return;
                }
            }
            throw new AuthBadRequestException("账号密码验证失败");
        } else if (ValidTypeEnum.MOBILE.getValue().equals(request.getValidType())
                || ValidTypeEnum.MAIL.getValue().equals(request.getValidType())) {
            AuthCodeParams code = new AuthCodeParams();
            code.setAuthCode(request.getAuthCode());
            code.setPrefix(request.getPrefix());
            code.setUserId(request.getUserId());
            code.setValidType(request.getValidType());
            if (authCodeService.validate(code)) {
                return;
            }
            throw new AuthBadRequestException(ValidTypeEnum.MOBILE.getValue().equals(request.getValidType())
                    ? "手机号码验证失败" : "电子邮箱验证失败");
        }
        throw new AuthBadRequestException("验证失败");
    }

    public void bindAccount(BindAccountRequest request) {
        if (Strings.isBlank(request.getValidType()) || Strings.isBlank(request.getUserId())
                || Strings.isBlank(request.getAuthCode()) || Strings.isBlank(request.getValidBox())) {
            throw new AuthBadRequestException("Parameter error");
        }
        User user = dao.queryForObject(User.class, request.getUserId());
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        AuthCodeParams code = new AuthCodeParams();
        code.setAuthCode(request.getAuthCode());
        code.setPrefix(request.getPrefix());
        code.setUserId(request.getUserId());
        code.setValidType(request.getValidType());
        code.setValidBox(request.getValidBox());
        if (ValidTypeEnum.PASSWORD.getValue().equals(request.getValidType())) {
            user.setPlainPassword(request.getValidBox());
            EncryptUtil.encryptPassword(user);
            dao.save(user);
            return;
        }
        if (!authCodeService.validate(code)) {
            throw new AuthBadRequestException("验证码错误");
        }
        if (ValidTypeEnum.MOBILE.getValue().equals(request.getValidType())) {
            user.setMobilePhone(request.getValidBox());
            user.setMobilePrefix(request.getPrefix());
            dao.save(user);
            return;
        }
        if (ValidTypeEnum.MAIL.getValue().equals(request.getValidType())) {
            user.setEmail(request.getValidBox());
            dao.save(user);
            return;
        }
        throw new AuthBadRequestException("绑定失败");
    }

    private AuthCodeParams buildAuthCodeParams(ForgetPasswordRequest request) {
        AuthCodeParams code = new AuthCodeParams();
        code.setAction(request.getAction());
        code.setAuthCode(request.getAuthCode());
        code.setPrefix(request.getPrefix());
        code.setValidType(request.getValidType());
        code.setValidBox(request.getValidBox());
        code.setUserId(request.getUserId());
        return code;
    }
}
