package cn.geelato.web.common.oauth2;

import cn.geelato.utils.HttpUtils;
import cn.geelato.web.common.interceptor.InvalidTokenException;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2Service {
    public OAuth2ServerTokenResult getToken(String baseUrl, String clientId, String clientSecret, String code) throws IOException {
        String url=String.format( "%s/oauth2/token?client_id=%s&client_secret=%s&grant_type=authorization_code&code=%s",
                baseUrl,clientId,clientSecret,code);
        String result=HttpUtils.doGet(url,null);
        try{
            return JSON.parseObject(result, OAuth2ServerTokenResult.class);
        } catch (Exception e) {
            throw new InvalidTokenException();
        }
    }

    public OAuth2ServerResult getUserInfo(String baseUrl, String accessToken) throws IOException {
        String url=String.format( "%s/oauth2/userinfo?access_token=%s",
                baseUrl,accessToken);
        String result=HttpUtils.doGet(url,null);
        return JSON.parseObject(result, OAuth2ServerResult.class);
    }
}
