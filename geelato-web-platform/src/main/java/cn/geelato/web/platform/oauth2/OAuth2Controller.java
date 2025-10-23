package cn.geelato.web.platform.oauth2;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.OAuthConfigurationProperties;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.oauth2.OAuth2ServerResult;
import cn.geelato.web.common.oauth2.OAuth2ServerTokenResult;
import cn.geelato.web.common.oauth2.OAuth2Service;
import cn.geelato.web.common.oauth2.TokenManager;
import cn.geelato.web.platform.m.security.entity.LoginResult;
import cn.geelato.meta.User;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.HashMap;

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
                // 存储token映射关系
                TokenManager.storeTokens(oAuthServerTokenResult.getAccess_token(), oAuthServerTokenResult.getRefresh_token());
                
                User user = JSON.parseObject(userInfoResult.getData(), User.class);
                LoginResult loginResult = LoginResult.formatLoginResult(user);
                loginResult.setToken(oAuthServerTokenResult.getAccess_token());
                return ApiResult.success(loginResult);
            }
        }
        return ApiResult.fail(oAuthServerTokenResult.getMsg());
    }
    
    /**
     * 刷新token
     * 根据旧token获取对应的refreshToken，然后使用refreshToken获取新token
     * 
     * @param oldToken 旧的访问令牌
     * @return 新的token信息
     */
    @RequestMapping(value = "/refreshToken", method = RequestMethod.POST)
    public ApiResult<HashMap<String, String>> refreshToken(String oldToken) {
        log.info("开始刷新token，旧token: {}", oldToken);
        
        // 根据旧token获取refreshToken
        String refreshToken = TokenManager.getRefreshToken(oldToken);
        if (refreshToken == null) {
            log.error("刷新token失败，未找到对应的refreshToken");
            return ApiResult.fail("未找到对应的refreshToken，请重新登录");
        }
        
        try {
            // 使用refreshToken获取新token
            OAuth2ServerTokenResult tokenResult = OAuth2Helper.refreshToken(
                    oAuthConfigurationProperties.getUrl(),
                    oAuthConfigurationProperties.getClientId(),
                    oAuthConfigurationProperties.getClientSecret(),
                    refreshToken);
            
            if (tokenResult != null && tokenResult.getCode().equals("200")) {
                // 解析返回的token数据
                JSONObject tokenData = com.alibaba.fastjson2.JSON.parseObject(tokenResult.getData());
                String newAccessToken = tokenData.getString("access_token");
                String newRefreshToken = tokenData.getString("refresh_token");
                
                // 更新token映射关系
                TokenManager.updateTokens(oldToken, newAccessToken, newRefreshToken);
                
                // 返回新token信息
                HashMap<String, String> result = new HashMap<>();
                result.put("access_token", newAccessToken);
                result.put("refresh_token", newRefreshToken);
                
                log.info("token刷新成功，新token: {}", newAccessToken);
                return ApiResult.success(result);
            } else {
                log.error("刷新token失败: {}", tokenResult != null ? tokenResult.getMsg() : "未知错误");
                return ApiResult.fail(tokenResult != null ? tokenResult.getMsg() : "刷新token失败");
            }
        } catch (Exception e) {
            log.error("刷新token异常", e);
            return ApiResult.fail("刷新token异常: " + e.getMessage());
        }
    }
}
