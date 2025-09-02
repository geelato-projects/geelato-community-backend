package cn.geelato.web.common.oauth2;

import cn.geelato.utils.HttpUtils;
import cn.geelato.web.common.security.User;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OAuth2Helper {

    public static User getUserInfo(String baseUrl, String accessToken) throws IOException {
        String url=String.format( "%s/oauth2/userinfo?access_token=%s",
                baseUrl,accessToken);

        String result= HttpUtils.doGet(url,null);
        OAuth2ServerResult oAuthServerResult= JSON.parseObject(result, OAuth2ServerResult.class);
        if(oAuthServerResult.getCode().equals("200")){
            return JSON.parseObject(oAuthServerResult.getData(), User.class);
        }else{
            return null;
        }
    }
    
    /**
     * 刷新访问令牌
     * @param baseUrl OAuth2服务器基础URL
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @param refreshToken 刷新令牌
     * @return 刷新后的token结果
     * @throws IOException IO异常
     */
    public static OAuth2ServerTokenResult refreshToken(String baseUrl, String clientId, String clientSecret, String refreshToken) throws IOException {
        String url = String.format("%s/oauth2/refresh?grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s", 
                                   baseUrl, clientId, clientSecret, refreshToken);
        
        String result = HttpUtils.doGet(url, null);
        return JSON.parseObject(result, OAuth2ServerTokenResult.class);
    }
}
