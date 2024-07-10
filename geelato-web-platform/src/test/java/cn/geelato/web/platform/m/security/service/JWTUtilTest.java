package cn.geelato.web.platform.m.security.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import cn.geelato.web.platform.m.base.service.RuleService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

/**
 * @author geelato
 */
@RunWith(SpringRunner.class)
public class JWTUtilTest {

    private RuleService ruleService = new RuleService();

    @Test
    public void getToken() throws Exception {
        //创建map
        Map<String, String> map = new HashMap<>();
        map.put("username", "geelato");
        map.put("id", "123456");
        //颁发token
        String token = JWTUtil.getToken(map);
        System.out.println("---------------------------");
        System.out.println(token);

        //解析token
        DecodedJWT verify = JWTUtil.verify(token);
        System.out.println(verify.getClaim("username").asString());
    }

}