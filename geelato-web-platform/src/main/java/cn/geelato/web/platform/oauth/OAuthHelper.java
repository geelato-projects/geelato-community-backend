package cn.geelato.web.platform.oauth;

import cn.geelato.utils.HttpUtils;
import cn.geelato.web.platform.boot.properties.OAuthConfigurationProperties;
import cn.geelato.web.platform.m.security.entity.User;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

public class OAuthHelper {

    public static User getUserInfo(String baseUrl, String accessToken) throws IOException {
        String url=String.format( "%s/oauth2/userinfo?access_token=%s",
                baseUrl,accessToken);
        String result= HttpUtils.doGet(url,null);
        OAuthServerResult oAuthServerResult= JSON.parseObject(result, OAuthServerResult.class);
        if(oAuthServerResult.getCode().equals("200")){
            return JSON.parseObject(oAuthServerResult.getData(), User.class);
        }else{
            return null;
        }
    }

    public static User getUserInfo(String accessToken) throws IOException {
        String url=String.format( "%s/oauth2/userinfo?access_token=%s",
                "https://www.geelato.cn",accessToken);
        String result= HttpUtils.doGet(url,null);
        OAuthServerResult oAuthServerResult= JSON.parseObject(result, OAuthServerResult.class);
        if(oAuthServerResult.getCode().equals("200")){
            return JSON.parseObject(oAuthServerResult.getData(), User.class);
        }else{
            return null;
        }
    }
}
