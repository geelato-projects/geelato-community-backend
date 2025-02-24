package cn.geelato.web.platform.m.security.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.AuthCodeParams;
import cn.geelato.web.platform.m.security.service.AuthCodeService;
import cn.geelato.web.platform.m.settings.entity.Message;
import cn.geelato.web.platform.m.settings.enums.MessageSendStatus;
import cn.geelato.web.platform.m.settings.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/code")
@Slf4j
public class AuthCodeController extends BaseController {
    private final AuthCodeService authCodeService;
    private final MessageService messageService;

    @Autowired
    public AuthCodeController(AuthCodeService authCodeService, MessageService messageService) {
        this.authCodeService = authCodeService;
        this.messageService = messageService;
    }

    @RequestMapping(value = "/generate/user", method = RequestMethod.POST)
    public ApiResult<NullResult> generateUser(@RequestBody Map<String, Object> params) {
        AuthCodeParams form = new AuthCodeParams();
        try {
            BeanUtils.populate(form, params);
            buildMessage(form);
            if (!authCodeService.generateUser(form)) {
                throw new RuntimeException("验证码生成失败");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            messageService.updateStatus(form.getMessage(), MessageSendStatus.FAIL.getValue());
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generate/auth", method = RequestMethod.POST)
    public ApiResult<NullResult> generateAuth(@RequestBody Map<String, Object> params) {
        AuthCodeParams form = new AuthCodeParams();
        try {
            BeanUtils.populate(form, params);
            buildMessage(form);
            if (!authCodeService.generateAuth(form)) {
                throw new RuntimeException("验证码生成失败");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            messageService.updateStatus(form.getMessage(), MessageSendStatus.FAIL.getValue());
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    public void buildMessage(AuthCodeParams form) {
        Message message = form.buildMessage();
        message = messageService.createModel(message);
        form.setMessage(message);
    }
}
