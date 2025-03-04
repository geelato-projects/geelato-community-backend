package cn.geelato.web.platform.oauth;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.boot.properties.OAuthConfigurationProperties;
import cn.geelato.web.platform.m.security.entity.LoginResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;

@ApiRestController("/oauth")
@Slf4j
public class OAuthController {

    private OAuthConfigurationProperties oAuthConfigurationProperties;
    private OAuthService oAuthService;
    @Autowired
    private OAuthController(OAuthConfigurationProperties oAuthConfigurationProperties,OAuthService oAuthService) {
        this.oAuthConfigurationProperties = oAuthConfigurationProperties;
        this.oAuthService = oAuthService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public ApiResult<LoginResult> login(String code) throws IOException {
        OAuthServerTokenResult oAuthServerTokenResult=oAuthService.getToken(
                oAuthConfigurationProperties.getUrl(),
                oAuthConfigurationProperties.getClientId(),
                oAuthConfigurationProperties.getClientSecret(),
                code
        );
        if(oAuthServerTokenResult.getCode().equals("200")){

        }
        return null;
    }
}
