package cn.geelato.web.platform.oauth;

import cn.geelato.utils.HttpUtils;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthService {
    public OAuthServerTokenResult getToken(String baseUrl,String clientId,String clientSecret, String code) throws IOException {
        String url=String.format( "%s/oauth2/token?client_id=%s&client_secret=%s&grant_type=authorization_code&code=%s",
                baseUrl,clientId,clientSecret,code);
        String result=HttpUtils.doGet(url,null);
        return JSON.parseObject(result, OAuthServerTokenResult.class);
    }

    public OAuthServerResult getUserInfo(String baseUrl,String accessToken) throws IOException {
        String url=String.format( "%s/oauth2/userinfo?access_token=%s",
                baseUrl,accessToken);
        String result=HttpUtils.doGet(url,null);
        return JSON.parseObject(result, OAuthServerResult.class);
    }
}
