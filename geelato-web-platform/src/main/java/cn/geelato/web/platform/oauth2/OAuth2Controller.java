package cn.geelato.web.platform.oauth2;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.OAuthConfigurationProperties;
import cn.geelato.web.common.oauth2.OAuth2ServerResult;
import cn.geelato.web.common.oauth2.OAuth2ServerTokenResult;
import cn.geelato.web.common.oauth2.OAuth2Service;
import cn.geelato.web.platform.m.security.entity.LoginResult;
import cn.geelato.web.platform.m.security.entity.User;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;

@ApiRestController("/oauth2")
@Slf4j
public class OAuth2Controller {

    private final OAuthConfigurationProperties oAuthConfigurationProperties;
    private final OAuth2Service oAuthService;
    @Autowired
    private OAuth2Controller(OAuthConfigurationProperties oAuthConfigurationProperties, OAuth2Service oAuthService) {
        this.oAuthConfigurationProperties = oAuthConfigurationProperties;
        this.oAuthService = oAuthService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public ApiResult<LoginResult> login(String code) throws IOException {
        OAuth2ServerTokenResult oAuthServerTokenResult = oAuthService.getToken(
                oAuthConfigurationProperties.getUrl(), oAuthConfigurationProperties.getClientId(),
                oAuthConfigurationProperties.getClientSecret(), code);

        if (oAuthServerTokenResult.getCode().equals("200")) {
            OAuth2ServerResult userInfoResult = oAuthService.getUserInfo(
                    oAuthConfigurationProperties.getUrl(),
                    oAuthServerTokenResult.getAccess_token());
            if (userInfoResult.getCode().equals("200")) {
                User user = JSON.parseObject(userInfoResult.getData(), User.class);
                LoginResult loginResult = LoginResult.formatLoginResult(user);
                loginResult.setToken(oAuthServerTokenResult.getAccess_token());
                return ApiResult.success(loginResult);
            }
        }
        return ApiResult.fail(oAuthServerTokenResult.getMsg());
    }
}
