package cn.geelato.web.common.oauth2;

import cn.geelato.security.User;
import cn.geelato.utils.HttpUtils;
import com.alibaba.fastjson2.JSON;

import java.io.IOException;

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
}
