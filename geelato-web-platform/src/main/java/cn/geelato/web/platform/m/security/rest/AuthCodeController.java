package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.AuthCodeParams;
import cn.geelato.web.platform.m.security.service.AuthCodeService;
import org.apache.commons.beanutils.BeanUtils;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.m.base.rest.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * @author diabl
 */
@Controller
@RequestMapping(value = "/api/code")
public class AuthCodeController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(AuthCodeController.class);
    @Autowired
    private AuthCodeService authCodeService;

    @RequestMapping(value = "/generate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult generate(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            if (authCodeService.generate(form)) {
                result.success();
            } else {
                result.error().setMsg(ApiErrorMsg.AUTH_CODE_GET_ERROR);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }
}
