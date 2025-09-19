package cn.geelato.web.wework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/wx/validate")
public class WeworkMsgController {
    private static final Logger logger = LoggerFactory.getLogger(WeworkMsgController.class);

    @Value("${wework.token:rPcRqcWuF}")
    private String token;

    @Value("${wework.aeskey:xBkfnZcxCI10PY8tGWEs5sjju1568zpx3M33XWyI4w1}")
    private String encodingAesKey;

    @Value("${wework.corpid:wwfed14f2fada336fd}")
    private String corpId;

    private WeworkMsgCryptUtil cryptUtil;

    /**
     * 初始化加密工具
     */
    private void initCryptUtil() {
        if (cryptUtil == null) {
            cryptUtil = new WeworkMsgCryptUtil(token, encodingAesKey, corpId);
        }
    }

    @GetMapping("/receive")
    public String verifyUrl(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        
        logger.info("接收到URL验证请求: msg_signature={}, timestamp={}, nonce={}, echostr={}", 
                msgSignature, timestamp, nonce, echostr);
        
        try {
            initCryptUtil();
            String result = cryptUtil.verifyUrl(msgSignature, timestamp, nonce, echostr);
            logger.info("URL验证成功，返回: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("URL验证失败", e);
            return "验证失败";
        }
    }


}