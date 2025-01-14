package cn.geelato.web.platform.m.security.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.AuthCodeParams;
import cn.geelato.web.platform.m.security.service.AuthCodeService;
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

    @Autowired
    public AuthCodeController(AuthCodeService authCodeService) {
        this.authCodeService = authCodeService;
    }

    @RequestMapping(value = "/generateByUser", method = RequestMethod.POST)
    public ApiResult<NullResult> generateByUser(@RequestBody Map<String, Object> params) {
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            if (!authCodeService.generateByUser(form)) {
                throw new RuntimeException("验证码生成失败");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/generate", method = RequestMethod.POST)
    public ApiResult<NullResult> generate(@RequestBody Map<String, Object> params) {
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            if (!authCodeService.generate(form)) {
                throw new RuntimeException("验证码生成失败");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
